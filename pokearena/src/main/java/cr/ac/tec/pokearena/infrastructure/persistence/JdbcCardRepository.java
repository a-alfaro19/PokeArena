package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.CatalogCriteria;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardRepository;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCatalogRepository;

/**
 * Propósito: implementación JDBC de {@link CardRepository} y {@link CollectibleCardRepository} (tablas
 * card y card_type_counter).
 * Responsabilidades: entregar el consecutivo de cada tipo de forma atómica, guardar cartas, leer el
 * destino de evolución de la habilidad de una carta, buscar cartas por criterios y elegir al azar las
 * cartas de la colección inicial, además del catálogo y las copias privadas de 8.3.
 * Referencia: sección 7.1 ("Código único" y paso 10), sección 7.3 (búsqueda) y sección 8.2 (pasos 3 y 4).
 *
 * <p>El consecutivo usa una sola sentencia {@code UPDATE ... RETURNING}: PostgreSQL bloquea la fila
 * del tipo hasta que la transacción termina, así dos transacciones del mismo tipo nunca reciben el
 * mismo número; y si la transacción se revierte, el incremento también se revierte (no se consume).
 * Por eso esta operación exige una transacción abierta.
 */
@Repository
public class JdbcCardRepository implements CardRepository, CollectibleCardRepository, PlayerCatalogRepository {

    /**
     * Selección al azar de la colección inicial (parámetros: rareza, semilla como texto y cantidad).
     * Es pública para que la prueba de rendimiento pida el plan de esta misma consulta.
     */
    public static final String PICK_COLLECTIBLE_SQL = """
            SELECT code FROM card
            WHERE rarity_code = ? AND available AND active
            ORDER BY md5(code || ?), code
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final AbilityRows abilityRows;

    public JdbcCardRepository(JdbcTemplate jdbc, AbilityRows abilityRows) {
        this.jdbc = jdbc;
        this.abilityRows = abilityRows;
    }

    @Override
    public int nextNumber(String typeCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "El consecutivo se obtiene dentro del UnitOfWork que guarda la carta (sección 7.1)");
        }
        return jdbc.query("""
                UPDATE card_type_counter SET last_number = last_number + 1
                WHERE type_code = ?
                RETURNING last_number
                """, (rs, row) -> rs.getInt("last_number"), typeCode).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay consecutivo para el tipo " + typeCode));
    }

    @Override
    public void save(Card card) {
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                card.code().value(), card.fields().name(), card.fields().nameNormalized(), card.fields().cost(),
                card.fields().power(), card.fields().type().code(), card.fields().rarity().code(),
                card.ability().code(), card.imageLargeUrl(), card.imageSmallUrl(), card.imageStatus().name(),
                card.available(), OffsetDateTime.ofInstant(card.createdAt(), ZoneOffset.UTC));
    }

    @Override
    public Optional<String> evolutionTargetOf(String cardCode) {
        return jdbc.query("""
                SELECT a.evolves_to_card_code
                FROM card c
                JOIN ability a ON a.code = c.ability_code
                WHERE c.code = ? AND a.evolves_to_card_code IS NOT NULL
                """, (rs, row) -> rs.getString(1), cardCode).stream().findFirst();
    }

    /** La consulta la arma {@link CardSearchSql}; aquí solo se ejecuta y se convierte cada fila. */
    @Override
    public List<StoredCard> search(CardSearchCriteria criteria, long afterId, int maxRows) {
        CardSearchSql.Query query = CardSearchSql.build(criteria, afterId, maxRows);
        return jdbc.query(query.sql(), (rs, row) -> storedCard(rs), query.arguments().toArray());
    }

    /** Catálogo compartible: mantiene la visibilidad del jugador dentro de la consulta paginada. */
    @Override
    public List<StoredCard> available(CatalogCriteria criteria, long afterId, int maxRows) {
        CardSearchSql.Query query = CardSearchSql.catalog(criteria.cardCriteria(), afterId, maxRows, null);
        return jdbc.query(query.sql(), (rs, row) -> storedCard(rs), query.arguments().toArray());
    }

    /** Colección privada: filtra antes del límite y obtiene las copias en la misma consulta. */
    @Override
    public List<OwnedCard> owned(UUID playerId, CatalogCriteria criteria, long afterId, int maxRows) {
        CardSearchSql.Query query = CardSearchSql.catalog(criteria.cardCriteria(), afterId, maxRows, playerId);
        return jdbc.query(query.sql(), (rs, row) -> new OwnedCard(storedCard(rs), rs.getInt("copies")),
                query.arguments().toArray());
    }

    /** Una consulta por página, no una por carta; no consulta nada para una página vacía. */
    @Override
    public Map<String, Integer> findCopies(UUID playerId, Collection<String> cardCodes) {
        if (cardCodes.isEmpty()) {
            return Map.of();
        }
        // Solo se genera la cantidad de placeholders; jugador y códigos siguen parametrizados.
        String placeholders = String.join(",", Collections.nCopies(cardCodes.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(playerId);
        arguments.addAll(cardCodes);
        Map<String, Integer> copies = new HashMap<>();
        jdbc.query("SELECT card_code, copies FROM player_card WHERE player_id = ? AND card_code IN ("
                + placeholders + ")", (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        copies.put(rs.getString("card_code"), rs.getInt("copies")), arguments.toArray());
        return copies;
    }

    private StoredCard storedCard(ResultSet rs) throws SQLException {
        CardFields fields = new CardFields(rs.getString("name"), rs.getString("name_normalized"),
                rs.getInt("cost"), rs.getInt("power"),
                new CardType(rs.getString("type_code"), rs.getString("type_name")),
                new Rarity(rs.getString("rarity_code"), rs.getString("rarity_name")));
        Card card = new Card(new CardCode(rs.getString("code")), fields, abilityRows.map(rs),
                rs.getString("image_large_url"), rs.getString("image_small_url"),
                ImageStatus.valueOf(rs.getString("image_status")), rs.getBoolean("available"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
        return new StoredCard(rs.getLong("id"), card);
    }

    // ---------------------------------------------------------------------------------------------
    // Cartas que un jugador puede recibir (CollectibleCardRepository, sección 8.2).
    // ---------------------------------------------------------------------------------------------

    @Override
    public int countCollectible(String rarityCode) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM card WHERE rarity_code = ? AND available AND active
                """, Integer.class, rarityCode);
    }

    /**
     * Ordena las cartas candidatas por {@code md5(code || semilla)} y toma las primeras: es un orden
     * pseudoaleatorio que depende solo de la semilla y del código, así la misma semilla da la misma
     * selección y semillas distintas dan otra. Las cartas son distintas porque {@code code} es único.
     * La base calcula el md5 de cada candidata y conserva solo las {@code count} menores (Top-N heapsort):
     * nunca devuelve ni guarda en memoria el catálogo completo. El código desempata, por si acaso.
     */
    @Override
    public List<String> pickRandomCollectible(String rarityCode, long seed, int count) {
        return jdbc.queryForList(PICK_COLLECTIBLE_SQL, String.class, rarityCode, Long.toString(seed), count);
    }
}
