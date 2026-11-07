package cr.ac.tec.pokearena.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cr.ac.tec.pokearena.domain.model.CubeMovement;
import cr.ac.tec.pokearena.domain.repository.CubeRepository;

/**
 * Propósito: implementación JDBC de {@link CubeRepository} (tabla cube_movement y columna player.cube_balance).
 * Responsabilidades: registrar el movimiento y aplicarlo al saldo en la misma transacción.
 * Referencia: sección 6, sección 8.2 (paso 6) y sección 9 (invariante "cube_balance = suma de movimientos").
 *
 * <p>Exige una transacción abierta: el movimiento y el saldo son dos sentencias, y sin transacción una
 * falla entre ambas rompería el invariante. Un saldo que quedaría negativo lo rechaza la restricción
 * {@code ck_player_cube_balance_non_negative} de la base, y la transacción se revierte.
 */
@Repository
public class JdbcCubeRepository implements CubeRepository {

    private final JdbcTemplate jdbc;

    public JdbcCubeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void apply(CubeMovement movement) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Los cubos se mueven dentro de un UnitOfWork (sección 9)");
        }
        jdbc.update("INSERT INTO cube_movement (player_id, amount, reason, created_at) VALUES (?, ?, ?, ?)",
                movement.playerId(), movement.amount(), movement.reason().name(),
                OffsetDateTime.ofInstant(movement.createdAt(), ZoneOffset.UTC));
        int updated = jdbc.update("UPDATE player SET cube_balance = cube_balance + ? WHERE id = ?",
                movement.amount(), movement.playerId());
        if (updated == 0) {
            throw new IllegalStateException("No existe el jugador " + movement.playerId() + " para mover sus cubos");
        }
    }
}
