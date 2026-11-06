package cr.ac.tec.pokearena.application.port;

import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: identidad de una clave de idempotencia (sección 5.4).
 * Responsabilidades: agrupar los tres datos que la hacen única: el valor del encabezado
 * {@code Idempotency-Key}, el usuario que la envió y la operación. Así dos usuarios, o dos
 * operaciones, pueden usar el mismo valor sin chocar.
 * Referencia: sección 5.4 ("única en (key, user_id, endpoint)").
 *
 * @param value    valor del encabezado {@code Idempotency-Key}
 * @param userId   id del jugador autenticado
 * @param endpoint operación, por ejemplo {@code POST /api/v1/cartas}
 */
public record IdempotencyKey(String value, UUID userId, String endpoint) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "El valor de la clave es obligatorio");
        Objects.requireNonNull(userId, "El usuario es obligatorio");
        Objects.requireNonNull(endpoint, "La operación es obligatoria");
    }
}
