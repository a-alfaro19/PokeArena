package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;

import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: resultado de un intento de la tarea de reintentos de miniaturas sobre una entidad.
 * Responsabilidades: decir qué entidad se intentó, cómo terminó y cuántos intentos lleva, para que el
 * adaptador lo registre en el log (el caso de uso no escribe logs: no depende de ninguna biblioteca).
 * Referencia: sección 7.1 ("Si falla la miniatura").
 *
 * @param kind       carta o ubicación
 * @param id         identificador de la entidad
 * @param outcome    cómo terminó el intento
 * @param retryCount intentos hechos, contando este
 */
public record ThumbnailRetryResult(ImageRules.Kind kind, long id, Outcome outcome, int retryCount) {

    /** Cómo terminó un intento. */
    public enum Outcome {
        /** Se generó la miniatura: la entidad quedó {@code READY} y disponible. */
        READY,
        /** Falló, pero quedan intentos: sigue {@code PENDING_RETRY}. */
        STILL_PENDING,
        /** Falló y se agotaron los intentos: pasó a {@code FAILED}. */
        FAILED,
        /** La versión grande ya no está en disco: no hay nada que reintentar, pasó a {@code FAILED}. */
        LARGE_IMAGE_MISSING
    }

    public ThumbnailRetryResult {
        Objects.requireNonNull(kind, "El tipo de entidad es obligatorio");
        Objects.requireNonNull(outcome, "El resultado es obligatorio");
    }
}
