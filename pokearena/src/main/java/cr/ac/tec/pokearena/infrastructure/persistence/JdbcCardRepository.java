package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardCode;
import cr.ac.tec.pokearena.domain.model.CardFields;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.Rarity;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

/**
 * Propósito: implementación JDBC de {@link CardRepository} (tablas card y card_type_counter).
 * Responsabilidades: entregar el consecutivo de cada tipo de forma atómica, guardar cartas, leer el
 * destino de evolución de la habilidad de una carta y buscar cartas por criterios.
 * Referencia: sección 7.1 ("Código único" y paso 10) y sección 7.3 (búsqueda).
 *
 * <p>El consecutivo usa una sola sentencia {@code UPDATE ... RETURNING}: PostgreSQL bloquea la fila
 * del tipo hasta que la transacción termina, así dos transacciones del mismo tipo nunca reciben el
 * mismo número; y si la transacción se revierte, el incremento también se revierte (no se consume).
 * Por eso esta operación exige una transacción abierta.
 */
@Repository
public class JdbcCardRepository implements CardRepository {

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
}
