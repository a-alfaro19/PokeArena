package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.repository.ActiveMatchLookup;

/** Consulta los participantes activos del esquema de la US 48. */
@Repository
public class JdbcActiveMatchLookup implements ActiveMatchLookup {
    private final JdbcTemplate jdbc;

    public JdbcActiveMatchLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findActiveMatchId(UUID playerId) {
        // El índice parcial de partidas activas acota esta lectura a un resultado por jugador.
        return jdbc.query("SELECT match_id FROM game_match_player WHERE player_id = ? AND active",
                (rs, row) -> rs.getObject("match_id", UUID.class), playerId).stream().findFirst();
    }
}
