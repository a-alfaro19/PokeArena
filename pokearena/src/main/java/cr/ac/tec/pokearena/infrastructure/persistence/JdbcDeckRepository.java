package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.model.NewDeck;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;

/**
 * Propósito: implementación JDBC de {@link DeckRepository} (tablas {@code deck} y {@code deck_card}, V3).
 * Responsabilidades: guardar un deck con sus cartas y leer los decks de un jugador, siempre filtrando por
 * el jugador de la sesión.
 * Referencia: sección 7.1 y sección 6.1 del Sprint 3; Anexo A.7 (mensaje 19) y A.8 (mensajes 9 y 19).
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li><b>Nombre repetido sin parsear mensajes:</b> {@code INSERT ... ON CONFLICT ON CONSTRAINT
 *       uq_deck_player_name DO NOTHING RETURNING id}. El nombre de la restricción va en el SQL; si no vuelve
 *       fila, el nombre ya está usado y se lanza {@code DECK_NAME_TAKEN}. Cualquier otra violación sigue
 *       saliendo como excepción, sin confundirse con esta. No hace falta el driver de PostgreSQL al compilar
 *       (su scope sigue en {@code runtime}).</li>
 *   <li><b>Concurrencia:</b> si otra transacción insertó el mismo nombre y aún no confirma, PostgreSQL hace
 *       esperar a esta. Si la otra confirma, aquí no vuelve fila ({@code DECK_NAME_TAKEN}); si revierte, esta
 *       inserta. Así, de dos creaciones simultáneas, una gana y la otra recibe el conflicto (C.7-A2).</li>
 *   <li><b>Transacción:</b> {@code save} exige la del {@code UnitOfWork} de quien llama (como
 *       {@link JdbcIdempotencyStore}): el deck, sus cartas y la clave de idempotencia se confirman juntos.
 *       Las cartas se insertan en un lote ({@code batchUpdate}): una ida a la base para las 12.</li>
 *   <li><b>Cartas del deck en UNA consulta:</b> {@code findByIdAndPlayer} une {@code deck_card} con la ficha
 *       de la carta (las mismas columnas y el mismo mapeo, {@link CardRows}, del catálogo y la búsqueda).</li>
 *   <li>Las lecturas no exigen transacción (sección 4.3: "las lecturas no usan transacción explícita").</li>
 * </ul>
 */
@Repository
public class JdbcDeckRepository implements DeckRepository {

    /** Nombre de la restricción única de V3 (nombre normalizado por jugador). */
    static final String NAME_CONSTRAINT = "uq_deck_player_name";

    private final JdbcTemplate jdbc;
    private final CardRows cardRows;

    public JdbcDeckRepository(JdbcTemplate jdbc, CardRows cardRows) {
        this.jdbc = jdbc;
        this.cardRows = cardRows;
    }

    @Override
    public StoredDeck save(NewDeck deck) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "El deck se guarda dentro del UnitOfWork del caso de uso, junto con sus cartas y la clave de"
                            + " idempotencia (sección 7.1, paso 6)");
        }
        List<Long> ids = jdbc.queryForList("INSERT INTO deck (player_id, name, name_normalized, created_at)\n"
                + "VALUES (?, ?, ?, ?)\n"
                + "ON CONFLICT ON CONSTRAINT " + NAME_CONSTRAINT + " DO NOTHING\n"
                + "RETURNING id", Long.class,
                deck.playerId(), deck.name(), deck.nameNormalized(),
                OffsetDateTime.ofInstant(deck.createdAt(), ZoneOffset.UTC));
        if (ids.isEmpty()) {
            throw DeckException.nameTaken("Ya tienes un deck llamado \"" + deck.name() + "\". Elige otro nombre.");
        }
        long id = ids.getFirst();

        List<Object[]> rows = new ArrayList<>();
        for (String code : deck.cardCodes()) {
            rows.add(new Object[] { id, code });
        }
        jdbc.batchUpdate("INSERT INTO deck_card (deck_id, card_code) VALUES (?, ?)", rows);

        return new StoredDeck(id, deck.playerId(), deck.name(), deck.cardCodes().size(), deck.createdAt());
    }

    @Override
    public Optional<Deck> findByIdAndPlayer(long deckId, UUID playerId) {
        // Una sola consulta: la ficha de cada carta (CardSearchSql) unida a deck_card y al deck del jugador.
        // Un deck ajeno no devuelve filas, igual que uno inexistente.
        List<Row> rows = jdbc.query("SELECT d.id AS deck_id, d.player_id AS deck_player_id, d.name AS deck_name,\n"
                + "       d.created_at AS deck_created_at, " + CardSearchSql.COLUMNS
                + CardSearchSql.FROM
                + "JOIN deck_card dc ON dc.card_code = c.code\n"
                + "JOIN deck d ON d.id = dc.deck_id\n"
                + "WHERE d.id = ? AND d.player_id = ?\n"
                + "ORDER BY c.code",
                (rs, row) -> new Row(deckHeader(rs), cardRows.map(rs).card()), deckId, playerId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Header header = rows.getFirst().header();
        List<Card> cards = rows.stream().map(Row::card).toList();
        return Optional.of(new Deck(
                new StoredDeck(header.id(), header.playerId(), header.name(), cards.size(), header.createdAt()),
                cards));
    }

    @Override
    public List<StoredDeck> findByPlayer(UUID playerId, long afterId, int maxRows) {
        // Paginación por cursor (sección 5.5) sobre idx_deck_player (player_id, id). La cantidad de cartas se
        // cuenta en la misma consulta: una por página, no una por deck.
        return jdbc.query("""
                SELECT d.id, d.player_id, d.name, d.created_at, count(dc.card_code) AS size
                FROM deck d
                LEFT JOIN deck_card dc ON dc.deck_id = d.id
                WHERE d.player_id = ? AND d.id > ?
                GROUP BY d.id
                ORDER BY d.id
                LIMIT ?
                """,
                (rs, row) -> new StoredDeck(rs.getLong("id"), rs.getObject("player_id", UUID.class),
                        rs.getString("name"), rs.getInt("size"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                playerId, afterId, maxRows);
    }

    @Override
    public List<String> findCardCodes(long deckId) {
        return jdbc.queryForList("SELECT card_code FROM deck_card WHERE deck_id = ? ORDER BY card_code",
                String.class, deckId);
    }

    private static Header deckHeader(ResultSet rs) throws SQLException {
        return new Header(rs.getLong("deck_id"), rs.getObject("deck_player_id", UUID.class),
                rs.getString("deck_name"), rs.getObject("deck_created_at", OffsetDateTime.class).toInstant());
    }

    /** Datos del deck repetidos en cada fila de la unión. */
    private record Header(long id, UUID playerId, String name, Instant createdAt) {
    }

    /** Una fila de la unión: el deck y una de sus cartas. */
    private record Row(Header header, Card card) {
    }
}
