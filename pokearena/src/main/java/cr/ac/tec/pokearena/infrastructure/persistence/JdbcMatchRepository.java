package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchLocation;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.model.MatchStatus;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.NewMatch;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;

/**
 * Propósito: implementación JDBC del puerto {@link MatchRepository} para las tablas V4.
 * Responsabilidades: guardar atómicamente el agregado completo y leerlo solo para un participante autorizado.
 * Referencia: sección 6.2; sección 7.3, paso 6; Anexos A.9, A.14 y A.15.
 *
 * <p>La escritura requiere la transacción del llamador. El id se genera en PostgreSQL y no se usa como semilla;
 * la lectura combina las filas de la partida con sus ubicaciones, asientos y fichas de cartas.
 */
@Repository
public class JdbcMatchRepository implements MatchRepository {

    private static final String PARTICIPANT_CARD_COLUMNS = """
            mp.seat, mp.player_id, mp.is_bot, mp.deck_id, mp.energy, mp.active,
            mc.position, mc.card_code, mc.zone,
            """ + CardSearchSql.COLUMNS;

    private static final String PARTICIPANT_CARD_FROM = """
            FROM game_match_player mp
            JOIN game_match_card mc ON mc.match_id = mp.match_id AND mc.seat = mp.seat
            JOIN card c ON c.code = mc.card_code
            JOIN card_type t ON t.code = c.type_code
            JOIN rarity r ON r.code = c.rarity_code
            JOIN ability a ON a.code = c.ability_code
            """;

    private final JdbcTemplate jdbc;
    private final CardRows cardRows;
    private final LocationRows locationRows;

    public JdbcMatchRepository(JdbcTemplate jdbc, CardRows cardRows, LocationRows locationRows) {
        this.jdbc = jdbc;
        this.cardRows = cardRows;
        this.locationRows = locationRows;
    }

    @Override
    public UUID save(NewMatch match) {
        requireTransaction();
        UUID id = jdbc.queryForObject("""
                INSERT INTO game_match
                    (id, context, vs_bot, status, seed, bet, turn, turn_started_at, turn_duration_seconds, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, match.context().name(), match.vsBot(), match.status().name(), match.seed(),
                match.bet(), match.turn(), offset(match.turnStartedAt()), match.turnDurationSeconds(),
                offset(match.createdAt()));
        jdbc.batchUpdate("""
                INSERT INTO game_match_player (match_id, seat, player_id, is_bot, deck_id, energy, active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, participantRows(id, match.participants()));
        jdbc.batchUpdate("""
                INSERT INTO game_match_location (match_id, slot, location_id)
                VALUES (?, ?, ?)
                """, locationRows(id, match.locations()));
        jdbc.batchUpdate("""
                INSERT INTO game_match_card (match_id, seat, position, card_code, zone)
                VALUES (?, ?, ?, ?, ?)
                """, cardRows(id, match.participants()));
        return id;
    }

    @Override
    public Optional<UUID> findActiveMatchId(UUID playerId) {
        return jdbc.query("""
                SELECT match_id
                FROM game_match_player
                WHERE player_id = ? AND active
                """, (rs, row) -> rs.getObject("match_id", UUID.class), playerId).stream().findFirst();
    }

    @Override
    public Optional<Match> findByIdForPlayer(UUID matchId, UUID playerId) {
        Optional<MatchHeader> header = jdbc.query("""
                SELECT m.id, m.context, m.vs_bot, m.status, m.seed, m.bet, m.turn, m.turn_started_at,
                       m.turn_duration_seconds, m.created_at
                FROM game_match m
                WHERE m.id = ?
                  AND EXISTS (
                      SELECT 1 FROM game_match_player requester
                      WHERE requester.match_id = m.id AND requester.player_id = ?
                  )
                """, (rs, row) -> readHeader(rs), matchId, playerId).stream().findFirst();
        if (header.isEmpty()) {
            return Optional.empty();
        }

        MatchHeader state = header.get();
        List<MatchLocation> locations = jdbc.query("""
                SELECT ml.slot, """ + LocationRows.COLUMNS + """
                FROM game_match_location ml
                JOIN location l ON l.id = ml.location_id
                WHERE ml.match_id = ?
                ORDER BY ml.slot
                """, (rs, row) -> new MatchLocation(rs.getInt("slot"), rs.getLong("location_id"),
                locationRows.mapLocation(rs)), state.id());

        Map<Integer, ParticipantBuilder> participants = new LinkedHashMap<>();
        Map<String, Card> catalog = new LinkedHashMap<>();
        jdbc.query("SELECT " + PARTICIPANT_CARD_COLUMNS + "\n" + PARTICIPANT_CARD_FROM + """
                WHERE mp.match_id = ?
                ORDER BY mp.seat, mc.position
                """, (rs, row) -> readParticipantCard(rs), state.id()).forEach(card -> {
                    ParticipantBuilder participant = participants.computeIfAbsent(card.seat(),
                            ignored -> new ParticipantBuilder(card));
                    participant.cards().add(card.matchCard());
                    Card prior = catalog.putIfAbsent(card.matchCard().cardCode(), card.catalogCard());
                    if (prior != null && !prior.equals(card.catalogCard())) {
                        throw new IllegalStateException("La ficha de una carta cambió durante la lectura de la partida.");
                    }
                });
        List<MatchParticipant> seats = participants.values().stream().map(ParticipantBuilder::build).toList();
        return Optional.of(new Match(state.id(), state.context(), state.vsBot(), state.status(), state.seed(),
                state.bet(), state.turn(), state.turnStartedAt(), state.turnDurationSeconds(), state.createdAt(),
                locations, seats, catalog));
    }

    private static List<Object[]> participantRows(UUID matchId, List<MatchParticipant> participants) {
        return participants.stream()
                .map(participant -> new Object[] { matchId, participant.seat(), participant.playerId(),
                        participant.isBot(), participant.deckId(), participant.energy(), participant.active() })
                .toList();
    }

    private static List<Object[]> locationRows(UUID matchId, List<MatchLocation> locations) {
        return locations.stream()
                .map(location -> new Object[] { matchId, location.slot(), location.locationId() })
                .toList();
    }

    private static List<Object[]> cardRows(UUID matchId, List<MatchParticipant> participants) {
        List<Object[]> rows = new ArrayList<>();
        for (MatchParticipant participant : participants) {
            for (MatchCard card : participant.cards()) {
                rows.add(new Object[] { matchId, participant.seat(), card.position(), card.cardCode(),
                        card.zone().name() });
            }
        }
        return rows;
    }

    private static MatchHeader readHeader(ResultSet rs) throws SQLException {
        return new MatchHeader(rs.getObject("id", UUID.class), MatchContext.valueOf(rs.getString("context")),
                rs.getBoolean("vs_bot"), MatchStatus.valueOf(rs.getString("status")), rs.getLong("seed"),
                rs.getInt("bet"), rs.getInt("turn"), rs.getObject("turn_started_at", OffsetDateTime.class).toInstant(),
                rs.getInt("turn_duration_seconds"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private ParticipantCardRow readParticipantCard(ResultSet rs) throws SQLException {
        MatchCard matchCard = new MatchCard(rs.getInt("position"), rs.getString("card_code"),
                MatchZone.valueOf(rs.getString("zone")));
        Card catalogCard = cardRows.map(rs).card();
        return new ParticipantCardRow(rs.getInt("seat"), rs.getObject("player_id", UUID.class),
                rs.getBoolean("is_bot"), (Long) rs.getObject("deck_id"), rs.getInt("energy"),
                rs.getBoolean("active"), matchCard, catalogCard);
    }

    private static OffsetDateTime offset(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "La partida se guarda dentro del UnitOfWork del llamador, junto con los asientos, ubicaciones y cartas.");
        }
    }

    /** Columnas de la partida, repetidas por cada fila al leer el encabezado. */
    private record MatchHeader(UUID id, MatchContext context, boolean vsBot, MatchStatus status, long seed, int bet,
            int turn, java.time.Instant turnStartedAt, int turnDurationSeconds, java.time.Instant createdAt) {
    }

    /** Datos de un asiento repetidos por cada carta en la consulta del agregado. */
    private record ParticipantCardRow(int seat, UUID playerId, boolean isBot, Long deckId, int energy, boolean active,
            MatchCard matchCard, Card catalogCard) {
    }

    /** Acumula las cartas de un asiento para construir el modelo inmutable. */
    private static final class ParticipantBuilder {
        private final ParticipantCardRow firstCard;
        private final List<MatchCard> cards = new ArrayList<>();

        private ParticipantBuilder(ParticipantCardRow firstCard) {
            this.firstCard = firstCard;
        }

        private List<MatchCard> cards() {
            return cards;
        }

        private MatchParticipant build() {
            return new MatchParticipant(firstCard.seat(), firstCard.playerId(), firstCard.isBot(), firstCard.deckId(),
                    firstCard.energy(), firstCard.active(), cards);
        }
    }
}
