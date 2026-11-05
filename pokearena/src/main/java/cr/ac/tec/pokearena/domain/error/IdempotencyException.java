package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: errores de la clave de idempotencia de los POST de creación (sección 5.4).
 * Responsabilidades: distinguir la clave ausente de la clave reutilizada con otro contenido.
 * Referencia: sección 5.3, tipos IDEMPOTENCY_KEY_REQUIRED e IDEMPOTENCY_KEY_REUSED.
 */
public final class IdempotencyException extends DomainException {

    /** Nombre del encabezado HTTP que lleva la clave. */
    public static final String IDEMPOTENCY_KEY_FIELD = "Idempotency-Key";

    private IdempotencyException(ErrorType type, String message) {
        super(type, message, IDEMPOTENCY_KEY_FIELD, null);
    }

    public static IdempotencyException required(String message) {
        return new IdempotencyException(ErrorType.IDEMPOTENCY_KEY_REQUIRED, message);
    }

    public static IdempotencyException reused(String message) {
        return new IdempotencyException(ErrorType.IDEMPOTENCY_KEY_REUSED, message);
    }
}
