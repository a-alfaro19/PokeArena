package cr.ac.tec.pokearena.api.error;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import cr.ac.tec.pokearena.api.error.ApiErrorCatalog.Entry;
import cr.ac.tec.pokearena.api.error.ApiErrorCatalog.HttpErrorType;
import cr.ac.tec.pokearena.api.web.CorrelationIdFilter;
import cr.ac.tec.pokearena.domain.error.DomainException;

/**
 * Propósito: construir la respuesta HTTP de un error con el formato único (sección 5.3).
 * Responsabilidades: armar el {@link ApiError}, fijar el status y el tipo de contenido JSON, y
 * repetir el {@code X-Correlation-Id} en el encabezado.
 * Referencia: secciones 5.3 y 5.6.
 *
 * <p>El tipo de contenido se fija explícitamente: así el error sale en JSON aunque el cliente
 * haya pedido otro formato en {@code Accept}, y nunca cae en una página de error de otro formato.
 */
final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    /** Respuesta para un error de dominio: el detalle es el mensaje de la excepción. */
    static ResponseEntity<ApiError> fromDomain(DomainException exception, String correlationId) {
        Entry entry = ApiErrorCatalog.of(exception.type());
        return build(entry, exception.type().name(), exception.getMessage(),
                exception.field().orElse(null), exception.allowedValues(), correlationId);
    }

    /** Respuesta para un error de protocolo con un detalle fijo y seguro. */
    static ResponseEntity<ApiError> fromProtocol(HttpErrorType type, String detail, String correlationId) {
        return build(type.entry(), type.name(), detail, null, null, correlationId);
    }

    /** Respuesta genérica con todos sus datos. */
    static ResponseEntity<ApiError> build(Entry entry, String type, String detail, String field,
            List<String> allowedValues, String correlationId) {
        ApiError body = new ApiError(type, entry.title(), entry.status().value(), detail, field,
                allowedValues, correlationId);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(entry.status())
                .contentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            response.header(CorrelationIdFilter.HEADER, correlationId);
        }
        return response.body(body);
    }

    /** Agrega el encabezado {@code Allow} a una respuesta 405. */
    static ResponseEntity<ApiError> withAllow(ResponseEntity<ApiError> response, String allowedMethods) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.set(HttpHeaders.ALLOW, allowedMethods);
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }
}
