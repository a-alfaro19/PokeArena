package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;

import cr.ac.tec.pokearena.application.port.IdempotencyKey;

/**
 * Propósito: datos de idempotencia de una solicitud de creación (sección 5.4).
 * Responsabilidades: llevar al caso de uso la clave, el hash del contenido y el status con que
 * responde la operación cuando tiene éxito. La API arma este valor; la aplicación lo guarda y lo
 * compara sin interpretar el status.
 * Referencia: sección 5.4.
 *
 * @param key           clave (valor, usuario y operación)
 * @param requestHash   hash SHA-256 del contenido de la solicitud
 * @param successStatus status de la respuesta exitosa (por ejemplo 201), para guardarlo y repetirlo
 */
public record IdempotentRequest(IdempotencyKey key, String requestHash, int successStatus) {

    public IdempotentRequest {
        Objects.requireNonNull(key, "La clave es obligatoria");
        Objects.requireNonNull(requestHash, "El hash de la solicitud es obligatorio");
    }
}
