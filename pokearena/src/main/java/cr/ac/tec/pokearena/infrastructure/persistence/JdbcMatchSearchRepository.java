package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;

/** Persistencia del alta, del emparejamiento, de la consulta y de la salida (A.9 a A.12). */
@Repository
public class JdbcMatchSearchRepository implements MatchSearchRepository {

    private final JdbcTemplate jdbc;
    private final Duration inactivityTimeout;

    public JdbcMatchSearchRepository(JdbcTemplate jdbc,
            @Value("${app.matchmaking.inactivity-timeout}") Duration inactivityTimeout) {
        this.jdbc = jdbc;
        this.inactivityTimeout = inactivityTimeout;
    }

    @Override
    public Optional<MatchSearch> findOpenByPlayer(UUID playerId) {
        // A.9: la restricción de una sola espera abarca todos los contextos del jugador.
        return jdbc.query("SELECT * FROM match_search WHERE player_id = ? AND status = 'WAITING'",
                (rs, row) -> read(rs), playerId).stream().findFirst();
    }

    @Override
    public Optional<MatchSearch> insertWaiting(UUID playerId, long deckId, MatchContext context, int elo, Instant now) {
        requireTransaction();
        // A.9: aislar el conflicto del índice parcial para que el llamador revierta y resuelva el reintento.
        Timestamp startedAt = Timestamp.from(now);
        return jdbc.query("""
                INSERT INTO match_search (id, player_id, deck_id, context, status, elo, created_at, last_polled_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'WAITING', ?, ?, ?)
                ON CONFLICT (player_id) WHERE status = 'WAITING' DO NOTHING
                RETURNING *
                """, (rs, row) -> read(rs), playerId, deckId, context.name(), elo, startedAt, startedAt)
                .stream().findFirst();
    }

    @Override
    public Optional<MatchSearch> touchAndFind(UUID id, UUID playerId, Instant now) {
        // Bloquear antes de decidir también permite devolver el estado confirmado por un emparejamiento concurrente.
        return jdbc.query("""
                WITH owned AS MATERIALIZED (
                    SELECT s.*, s.last_polled_at < ? AS stale
                    FROM match_search s WHERE s.id = ? AND s.player_id = ? FOR UPDATE
                ), updated AS (
                    UPDATE match_search s
                    SET status = CASE WHEN owned.stale THEN 'EXPIRED' ELSE 'WAITING' END,
                        last_polled_at = CASE WHEN owned.stale THEN s.last_polled_at
                                              ELSE greatest(s.last_polled_at, ?) END,
                        closed_at = CASE WHEN owned.stale THEN ? ELSE s.closed_at END
                    FROM owned WHERE s.id = owned.id AND owned.status = 'WAITING'
                    RETURNING s.*
                )
                SELECT id, player_id, deck_id, context, status, elo, match_id, notice, not_before,
                       created_at, last_polled_at, closed_at FROM updated
                UNION ALL
                SELECT id, player_id, deck_id, context, status, elo, match_id, notice, not_before,
                       created_at, last_polled_at, closed_at FROM owned WHERE status <> 'WAITING'
                """, (rs, row) -> read(rs), Timestamp.from(now.minus(inactivityTimeout)), id, playerId,
                Timestamp.from(now), Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public int expireStale(Instant cutoff, Instant now) {
        // A.10, mensajes 4 y 5. PostgreSQL vuelve a comprobar el predicado si una consulta
        // o un cierre concurrente modificó la fila antes de obtener el bloqueo.
        return jdbc.update("""
                UPDATE match_search SET status = 'EXPIRED', closed_at = ?
                WHERE status = 'WAITING' AND last_polled_at < ?
                """, Timestamp.from(now), Timestamp.from(cutoff));
    }

    @Override
    public Optional<MatchSearch> lockOldestWaiting(Instant now, Instant cutoff, MatchSearch after) {
        requireTransaction();
        // A.10: el cursor incluye el id para avanzar incluso si varias búsquedas nacieron a la misma hora.
        Timestamp afterCreated = after == null ? null : Timestamp.from(after.createdAt());
        UUID afterId = after == null ? null : after.id();
        return jdbc.query("""
                SELECT * FROM match_search
                WHERE status = 'WAITING' AND last_polled_at >= ?
                  AND (not_before IS NULL OR not_before <= ?)
                  AND (?::timestamptz IS NULL OR (created_at, id) > (?, ?))
                ORDER BY created_at, id
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> read(rs), Timestamp.from(cutoff), Timestamp.from(now), afterCreated,
                afterCreated, afterId).stream().findFirst();
    }

    @Override
    public List<MatchSearch> lockCandidates(MatchSearch anchor, Instant now, Instant cutoff, int maxRange, int limit) {
        requireTransaction();
        // A.10: SQL acota el lote por el rango máximo. Java aplica la ventana efectiva de cada jugador.
        return jdbc.query("""
                SELECT * FROM match_search
                WHERE status = 'WAITING' AND player_id <> ? AND context = ? AND last_polled_at >= ?
                  AND (not_before IS NULL OR not_before <= ?)
                  AND abs(elo::bigint - ?) <= ?
                ORDER BY abs(elo::bigint - ?), created_at, id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> read(rs), anchor.playerId(), anchor.context().name(), Timestamp.from(cutoff),
                Timestamp.from(now), anchor.elo(), maxRange, anchor.elo(), limit);
    }

    @Override
    public int markNotice(UUID first, UUID second, Instant retryAt) {
        requireTransaction();
        // A.10: esperar por orden de id evita cruces entre avisos y respeta un cierre ya confirmado.
        return jdbc.update("""
                WITH waiting AS (
                    SELECT id FROM match_search WHERE id IN (?, ?) AND status = 'WAITING'
                    ORDER BY id FOR UPDATE
                )
                UPDATE match_search s SET notice = 'LOCATIONS_UNAVAILABLE', not_before = greatest(s.not_before, ?)
                FROM waiting WHERE s.id = waiting.id
                """, first, second, Timestamp.from(retryAt));
    }

    @Override
    public Optional<MatchSearch> lockById(UUID id, UUID playerId) {
        requireTransaction();
        // A.12, mensaje 9: esperar al emparejador si ya adquirió el bloqueo.
        return jdbc.query("SELECT * FROM match_search WHERE id = ? AND player_id = ? FOR UPDATE",
                (rs, row) -> read(rs), id, playerId).stream().findFirst();
    }

    @Override
    public MatchSearch markMatched(UUID id, UUID matchId, Instant now) {
        requireTransaction();
        return jdbc.query("""
                UPDATE match_search SET status = 'MATCHED', match_id = ?, closed_at = ?,
                    notice = NULL, not_before = NULL
                WHERE id = ? AND status = 'WAITING' RETURNING *
                """, (rs, row) -> read(rs), matchId, Timestamp.from(now), id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Se esperaba una búsqueda bloqueada en espera."));
    }

    @Override
    public MatchSearch markCancelled(UUID id, Instant now) {
        requireTransaction();
        // A.12, mensaje 14: devolver las fechas con la precisión guardada por PostgreSQL.
        return jdbc.query("""
                UPDATE match_search SET status = 'CANCELLED', closed_at = ?
                WHERE id = ? AND status = 'WAITING' RETURNING *
                """, (rs, row) -> read(rs), Timestamp.from(now), id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Se esperaba una búsqueda bloqueada en espera."));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La operación de búsqueda requiere una transacción activa.");
        }
    }

    private static MatchSearch read(ResultSet rs) throws SQLException {
        return new MatchSearch(rs.getObject("id", UUID.class), rs.getObject("player_id", UUID.class),
                rs.getLong("deck_id"), MatchContext.valueOf(rs.getString("context")),
                MatchSearchStatus.valueOf(rs.getString("status")), rs.getInt("elo"),
                rs.getObject("match_id", UUID.class), rs.getString("notice"), instant(rs, "not_before"),
                instant(rs, "created_at"), instant(rs, "last_polled_at"), instant(rs, "closed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
