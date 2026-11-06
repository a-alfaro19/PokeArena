package cr.ac.tec.pokearena.application.port;

import java.util.Optional;

import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;

/**
 * Propósito: puerto del almacén de claves de idempotencia (sección 5.4).
 * Responsabilidades: buscar la respuesta guardada de una clave y guardar una nueva. La unicidad
 * de la clave la garantiza el almacén (en PostgreSQL, la clave primaria), no quien lo llama.
 * Referencia: sección 5.4.
 *
 * <p>Está en {@code application.port} y no en {@code domain.repository} porque guarda respuestas
 * de la API, no entidades del juego.
 */
public interface IdempotencyStore {

    /** Respuesta guardada para la clave, si existe y ya está confirmada. */
    Optional<StoredResponse> find(IdempotencyKey key);

    /**
     * Guarda la respuesta de la clave. Debe llamarse dentro de la transacción que crea la entidad,
     * para que ambas se confirmen o se reviertan juntas.
     *
     * @throws IdempotencyConflictException si otra solicitud ya guardó la misma clave
     */
    void save(IdempotencyKey key, StoredResponse response);
}
