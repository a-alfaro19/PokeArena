package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: un movimiento del historial de cubos de un jugador (sección 6, tabla {@code cube_movement}).
 * Responsabilidades: llevar el jugador, el monto (positivo suma y negativo resta), el motivo y la fecha.
 * Referencia: sección 6 ("cube_balance = suma de movimientos") y sección 8.2, paso 6.
 *
 * @param playerId  jugador dueño del movimiento
 * @param amount    cubos que suma (positivo) o resta (negativo); nunca cero
 * @param reason    motivo del movimiento
 * @param createdAt momento del movimiento, tomado del puerto Clock
 */
public record CubeMovement(UUID playerId, long amount, CubeMovementReason reason, Instant createdAt) {

    public CubeMovement {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
        Objects.requireNonNull(reason, "El motivo es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha es obligatoria");
        if (amount == 0) {
            throw new IllegalArgumentException("Un movimiento de cubos no puede ser de cero");
        }
    }
}
