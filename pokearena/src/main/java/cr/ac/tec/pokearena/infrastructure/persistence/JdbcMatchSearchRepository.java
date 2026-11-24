package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;

/** Consulta atómica con comprobación de titularidad y de inactividad (A.11, mensaje 8). */
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
