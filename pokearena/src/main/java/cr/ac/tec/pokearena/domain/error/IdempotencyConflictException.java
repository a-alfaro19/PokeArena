package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: señal de que otra solicitud ya guardó la misma clave de idempotencia (sección 5.4).
 * Responsabilidades: avisar, desde el almacén de claves, que la clave (key, usuario, endpoint) ya
 * existe, para que la aplicación revierta su transacción y devuelva la respuesta que ya quedó.
 * Referencia: sección 5.4 ("Dos solicitudes iguales y simultáneas producen una sola entidad y una
 * sola respuesta correcta, no un 500").
 *
 * <p>No es una {@link DomainException}: no representa un error para el cliente, sino una carrera
 * entre dos solicitudes que {@code IdempotencyGuard} resuelve siempre. Por eso no tiene tipo de
 * error ni status HTTP. Si alguna vez escapara sin resolverse, sería un defecto del código y el
 * manejador global la informaría como INTERNAL_ERROR.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
