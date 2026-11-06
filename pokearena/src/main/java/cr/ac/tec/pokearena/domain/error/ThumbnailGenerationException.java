package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: señal de que no se pudo generar la versión pequeña de una imagen ya validada
 * (sección 7.1, "Si falla la miniatura"; ADR-002).
 * Responsabilidades: avisar al caso de uso, que decide qué hacer: guardar la entidad no disponible
 * con {@code image_status = PENDING_RETRY} para que la tarea programada reintente.
 * Referencia: sección 7.1 y Anexo B, ADR-002.
 *
 * <p>No es una {@link DomainException}: no es un error de la solicitud (la imagen ya pasó la
 * validación), así que no tiene tipo de error ni status HTTP. El caso de uso siempre la atiende;
 * si escapara, el manejador global la informaría como INTERNAL_ERROR.
 */
public class ThumbnailGenerationException extends RuntimeException {

    public ThumbnailGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
