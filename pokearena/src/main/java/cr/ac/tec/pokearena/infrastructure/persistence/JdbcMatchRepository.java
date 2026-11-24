package cr.ac.tec.pokearena.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Propósito: implementación JDBC de {@link MatchRepository} para las tablas game_match*.
 * Responsabilidades: guardar todo el estado inicial en la transacción del llamador y cargar una partida solo
 * para uno de sus participantes, junto con ubicaciones y fichas de cartas.
 * Referencia: secciones 6.2 y 7.3; Anexos A.14 y A.15.
 *
 * <p>El identificador UUID se genera en infraestructura: la semilla de la partida sigue siendo el único dato
 * aleatorio que usa el dominio para seleccionar ubicaciones y barajar decks.
 */
@Repository
public class JdbcMatchRepository implements MatchRepository {

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
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "La partida se guarda dentro de la transacción del caso de uso que forma la pareja (sección 7.3)");
        }
        UUID matchId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO game_match (id, context, vs_bot, status, seed, bet, turn, turn_started_at,
                                        turn_duration_seconds, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                matchId, match.context().name(), match.vsBot(), match.status().name(), match.seed(), match.bet(),
                match.turn(), timestamp(match.turnStartedAt()), match.turnDurationSeconds(),
                timestamp(match.createdAt()));

        jdbc.batchUpdate("""
                INSERT INTO game_match_player (match_id, seat, player_id, is_bot, deck_id, energy, active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, match.participants().stream()
                .map(participant -> new Object[] { matchId, participant.seat(), participant.playerId(),
                        participant.isBot(), participant.deckId(), participant.energy(), participant.active() })
                .toList());

        jdbc.batchUpdate("""
                INSERT INTO game_match_location (match_id, slot, location_id)
                VALUES (?, ?, ?)
                """, match.locations().stream()
                .map(location -> new Object[] { matchId, location.slot(), location.id() })
                .toList());

        List<Object[]> cardRows = new ArrayList<>();
        for (MatchParticipant participant : match.participants()) {
            for (MatchCard card : participant.cards()) {
                cardRows.add(new Object[] { matchId, participant.seat(), card.position(), card.code(),
                        card.zone().name() });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO game_match_card (match_id, seat, position, card_code, zone)
                VALUES (?, ?, ?, ?, ?)
                """, cardRows);
        return matchId;
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
        List<Header> headers = jdbc.query("""
                SELECT gm.id, gm.context, gm.vs_bot, gm.status, gm.seed, gm.bet, gm.turn,
                       gm.turn_started_at, gm.turn_duration_seconds, gm.created_at
                FROM game_match gm
                WHERE gm.id = ?
                  AND EXISTS (
                      SELECT 1 FROM game_match_player gmp
                      WHERE gmp.match_id = gm.id AND gmp.player_id = ?
                  )
                """, (rs, row) -> new Header(
                rs.getObject("id", UUID.class), rs.getString("context"), rs.getBoolean("vs_bot"),
                rs.getString("status"), rs.getLong("seed"), rs.getInt("bet"), rs.getInt("turn"),
                rs.getObject("turn_started_at", OffsetDateTime.class).toInstant(),
                rs.getInt("turn_duration_seconds"), rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                matchId, playerId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Header header = headers.getFirst();

        List<MatchLocation> locations = jdbc.query("""
                SELECT ml.slot, %s
                FROM game_match_location ml
                JOIN location l ON l.id = ml.location_id
                WHERE ml.match_id = ?
                ORDER BY ml.slot
                """.formatted(LocationRows.COLUMNS),
                (rs, row) -> new MatchLocation(rs.getInt("slot"), rs.getLong("location_id"),
                        locationRows.map(rs)),
                matchId);

        Map<Integer, List<MatchCard>> cardsBySeat = new HashMap<>();
        Map<String, Card> cardsByCode = new HashMap<>();
        jdbc.query("SELECT gmp.seat AS match_seat, gmc.position AS match_position, "
                + "gmc.zone AS match_zone, " + CardSearchSql.COLUMNS + CardSearchSql.FROM
                + """
                JOIN game_match_card gmc ON gmc.card_code = c.code
                JOIN game_match_player gmp ON gmp.match_id = gmc.match_id AND gmp.seat = gmc.seat
                WHERE gmc.match_id = ?
                ORDER BY gmp.seat, gmc.position
                """, rs -> {
                    int seat = rs.getInt("match_seat");
                    Card card = cardRows.map(rs).card();
                    cardsBySeat.computeIfAbsent(seat, ignored -> new ArrayList<>())
                            .add(new MatchCard(card.code().value(), rs.getInt("match_position"),
                                    MatchZone.valueOf(rs.getString("match_zone"))));
                    cardsByCode.putIfAbsent(card.code().value(), card);
                }, matchId);

        List<MatchParticipant> participants = jdbc.query("""
                SELECT seat, player_id, is_bot, deck_id, energy, active
                FROM game_match_player
                WHERE match_id = ?
                ORDER BY seat
                """, (rs, row) -> new MatchParticipant(
                rs.getInt("seat"), rs.getObject("player_id", UUID.class), rs.getBoolean("is_bot"),
                (Long) rs.getObject("deck_id"), rs.getInt("energy"), rs.getBoolean("active"),
                cardsBySeat.getOrDefault(rs.getInt("seat"), List.of())), matchId);

        return Optional.of(new Match(header.id(), MatchContext.valueOf(header.context()),
                header.vsBot(), MatchStatus.valueOf(header.status()), header.seed(), header.bet(), header.turn(),
                header.turnStartedAt(), header.turnDurationSeconds(), header.createdAt(), locations, participants,
                cardsByCode));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Header(UUID id, String context, boolean vsBot, String status, long seed, int bet, int turn,
            Instant turnStartedAt, int turnDurationSeconds, Instant createdAt) {
    }
}
