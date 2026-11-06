package cr.ac.tec.pokearena.application.usecase;

import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.IdempotencyStore;
import cr.ac.tec.pokearena.application.port.ResponseSerializer;
import cr.ac.tec.pokearena.application.port.StoredResponse;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.IdempotencyException;

/**
 * Propósito: idempotencia de los POST de creación, compartida por todos los casos de uso (sección 5.4).
 * Responsabilidades: (a) consultar si la clave ya tiene una respuesta guardada; (b) registrar la
 * respuesta dentro de la transacción del caso de uso; (c) resolver el choque con una solicitud
 * simultánea devolviendo la respuesta que ya quedó.
 * Referencia: sección 5.4 y pasos 4 y 10 de los diagramas A.1 y A.2.
 *
 * <p>Cómo lo usa un caso de uso de creación (misma plantilla en cartas y ubicaciones):
 * <pre>{@code
 * // Paso 4: solicitud previa con la clave.
 * Optional<IdempotentResult<Ficha>> previous = guard.findPrevious(request, Ficha.class);
 * if (previous.isPresent()) return previous.get();
 * // Pasos 5 a 9: validar y efectos fuera de la transacción (por ejemplo, guardar archivos).
 * try {
 *     // Paso 10: la entidad y la clave en la misma transacción.
 *     Ficha ficha = unitOfWork.execute(() -> { Ficha f = crear(...); guard.record(request, f); return f; });
 *     return IdempotentResult.executed(request, ficha);
 * } catch (IdempotencyConflictException conflict) {
 *     // Otra solicitud igual ganó: se compensan los efectos (borrar archivos) y se repite su respuesta.
 *     compensar();
 *     return guard.resolveConflict(request, Ficha.class);
 * }
 * }</pre>
 *
 * <p>Por qué funciona con solicitudes simultáneas: las dos pueden pasar la consulta (a) antes de que
 * la otra confirme. Pero al registrar (b), la clave primaria de la tabla obliga a la segunda a
 * esperar a la primera y luego falla; su transacción se revierte completa (no queda su entidad) y
 * (c) le entrega la respuesta de la primera. Ninguna recibe un 500.
 */
public class IdempotencyGuard {

    private static final String KEY_REUSED_MESSAGE =
            "La clave Idempotency-Key ya se usó con una solicitud de contenido distinto.";

    private final IdempotencyStore store;
    private final ResponseSerializer serializer;

    public IdempotencyGuard(IdempotencyStore store, ResponseSerializer serializer) {
        this.store = store;
        this.serializer = serializer;
    }

    /**
     * (a) Respuesta guardada para la clave, si existe.
     *
     * @return la respuesta anterior si el contenido es el mismo; vacío si la clave es nueva
     * @throws IdempotencyException IDEMPOTENCY_KEY_REUSED si la clave se usó con otro contenido
     */
    public <T> Optional<IdempotentResult<T>> findPrevious(IdempotentRequest request, Class<T> resultType) {
        return store.find(request.key()).map(stored -> replay(request, stored, resultType));
    }

    /**
     * (b) Guarda la respuesta de esta solicitud. Debe llamarse DENTRO del {@code UnitOfWork} que
     * crea la entidad: si la transacción se revierte, la clave tampoco queda.
     *
     * @throws IdempotencyConflictException si una solicitud simultánea ya guardó la misma clave
     */
    public void record(IdempotentRequest request, Object result) {
        store.save(request.key(),
                new StoredResponse(request.requestHash(), request.successStatus(), serializer.serialize(result)));
    }

    /**
     * (c) Después de un {@link IdempotencyConflictException}, ya fuera de la transacción revertida,
     * devuelve la respuesta que guardó la solicitud que ganó.
     *
     * @throws IdempotencyException IDEMPOTENCY_KEY_REUSED si la otra solicitud tenía otro contenido
     */
    public <T> IdempotentResult<T> resolveConflict(IdempotentRequest request, Class<T> resultType) {
        // La otra transacción ya confirmó (por eso hubo conflicto), así que su respuesta es visible.
        return findPrevious(request, resultType).orElseThrow(() -> new IllegalStateException(
                "Hubo conflicto con la clave " + request.key() + " pero no se encontró la respuesta guardada"));
    }

    private <T> IdempotentResult<T> replay(IdempotentRequest request, StoredResponse stored, Class<T> resultType) {
        // Misma clave con otro contenido: no se repite una respuesta que no corresponde (422).
        if (!stored.requestHash().equals(request.requestHash())) {
            throw IdempotencyException.reused(KEY_REUSED_MESSAGE);
        }
        return new IdempotentResult<>(stored.status(), serializer.deserialize(stored.body(), resultType), true);
    }
}
