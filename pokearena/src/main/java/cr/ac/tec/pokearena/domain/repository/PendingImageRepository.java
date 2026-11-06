package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.ImageStatus;
import cr.ac.tec.pokearena.domain.model.PendingImage;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: puerto de persistencia de la tarea de reintentos de miniaturas (patrón Repository).
 * Responsabilidades: tomar la siguiente carta o ubicación pendiente, bloqueándola para que otra
 * instancia no la procese a la vez, y guardar el resultado de cada intento.
 * Referencia: sección 7.1 ("Si falla la miniatura"), sección 7.2 ("mismo manejo de fallo") y sección 9
 * (bloqueos de fila explícitos donde se necesita "exactamente una vez").
 *
 * <p>Las tres operaciones deben llamarse dentro del mismo {@code UnitOfWork}: el bloqueo de
 * {@link #lockNextPending} dura hasta que la transacción termina.
 */
public interface PendingImageRepository {

    /**
     * Bloquea y devuelve la siguiente entidad de ese tipo en {@code PENDING_RETRY}, con menos de
     * {@code maxRetries} intentos y {@code id} mayor que {@code afterId}, en orden de {@code id}. Salta
     * las que otra transacción ya tiene bloqueadas: dos instancias nunca procesan la misma.
     */
    Optional<PendingImage> lockNextPending(ImageRules.Kind kind, int maxRetries, long afterId);

    /** Intento logrado: guarda la miniatura, pasa a {@code READY}, disponible, y suma un intento. */
    void markReady(ImageRules.Kind kind, long id, String smallUrl);

    /**
     * Intento fallido: suma un intento y deja el estado indicado ({@code PENDING_RETRY} si quedan
     * intentos, {@code FAILED} si no), siempre no disponible.
     */
    void markAttemptFailed(ImageRules.Kind kind, long id, ImageStatus status);
}
