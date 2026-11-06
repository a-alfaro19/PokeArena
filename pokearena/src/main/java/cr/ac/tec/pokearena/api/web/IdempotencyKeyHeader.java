package cr.ac.tec.pokearena.api.web;

import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.port.IdempotencyKey;

/**
 * Propósito: encabezado {@code Idempotency-Key} ya validado, listo para un controlador de creación
 * (sección 5.4).
 * Responsabilidades: transportar el valor de la clave y la operación a la que pertenece, y armar
 * el {@link IdempotentRequest} del caso de uso. Lo entrega {@link IdempotencyKeyHeaderResolver}:
 * un controlador solo lo declara como parámetro.
 * Referencia: sección 5.4 y paso 1 de los diagramas A.1 y A.2.
 *
 * @param value    valor del encabezado, no vacío y dentro del largo máximo
 * @param endpoint operación, por ejemplo {@code POST /api/v1/cartas}
 */
public record IdempotencyKeyHeader(String value, String endpoint) {

    /**
     * Arma los datos de idempotencia de la solicitud.
     *
     * @param userId        jugador autenticado dueño de la clave
     * @param requestHash   hash del contenido ({@code RequestHash})
     * @param successStatus status de la respuesta exitosa, por ejemplo 201
     */
    public IdempotentRequest toRequest(UUID userId, String requestHash, int successStatus) {
        return new IdempotentRequest(new IdempotencyKey(value, userId, endpoint), requestHash, successStatus);
    }
}
