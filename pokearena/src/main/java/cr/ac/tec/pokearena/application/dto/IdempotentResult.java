package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: resultado de una operación idempotente (sección 5.4).
 * Responsabilidades: entregar a la API el status y el cuerpo de la respuesta, sean de esta
 * ejecución o de una respuesta guardada antes. La API responde igual en ambos casos: la misma
 * solicitud recibe la misma respuesta.
 * Referencia: sección 5.4.
 *
 * @param status   status de la respuesta
 * @param body     resultado del caso de uso
 * @param replayed {@code true} si la respuesta se tomó de una solicitud anterior y no se ejecutó nada
 * @param <T>      tipo del resultado del caso de uso
 */
public record IdempotentResult<T>(int status, T body, boolean replayed) {

    /** Resultado de una ejecución nueva, con el status de éxito de la solicitud. */
    public static <T> IdempotentResult<T> executed(IdempotentRequest request, T body) {
        return new IdempotentResult<>(request.successStatus(), body, false);
    }
}
