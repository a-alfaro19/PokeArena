package cr.ac.tec.pokearena.application.port;

import java.util.Objects;

/**
 * Propósito: respuesta guardada junto con su clave de idempotencia (sección 5.4).
 * Responsabilidades: conservar lo necesario para repetir una respuesta sin repetir la operación:
 * el hash de la solicitud original (para detectar otro contenido), el status y el cuerpo.
 * Referencia: sección 5.4 (columnas request_hash, response_status y response_body).
 *
 * @param requestHash hash SHA-256 de la solicitud original
 * @param status      status de la respuesta original
 * @param body        cuerpo de la respuesta original, en JSON
 */
public record StoredResponse(String requestHash, int status, String body) {

    public StoredResponse {
        Objects.requireNonNull(requestHash, "El hash de la solicitud es obligatorio");
        Objects.requireNonNull(body, "El cuerpo de la respuesta es obligatorio");
    }
}
