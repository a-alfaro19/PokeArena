package cr.ac.tec.pokearena.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.error.ApiErrorCatalog.HttpErrorType;
import cr.ac.tec.pokearena.api.web.CorrelationIdFilter;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Propósito: red de seguridad del formato único para los errores que no pasan por
 * {@link ApiErrorHandler} (sección 5.3: "nunca debe quedar un error con otro formato").
 * Responsabilidades: atender la ruta {@code /error}, a la que el servidor despacha los errores
 * ocurridos fuera de los controladores (una excepción en un filtro o un {@code sendError}), y
 * responder con {@link ApiError} en lugar de la página o el JSON por defecto de Spring Boot.
 * Referencia: secciones 5.3 y 5.6.
 *
 * <p>Al declarar un {@code ErrorController}, Spring Boot no registra su {@code BasicErrorController}.
 */
@RestController
public class ApiFallbackErrorController implements ErrorController {

    private static final Logger LOG = LoggerFactory.getLogger(ApiFallbackErrorController.class);

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ApiError> handleError(HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.currentId(request);
        HttpStatusCode status = statusOf(request);

        // El MDC ya se limpió cuando el servidor despacha a /error: se repone para este log.
        Object failure = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (failure instanceof Throwable exception) {
            try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, correlationId)) {
                LOG.error("Error inesperado fuera de un controlador en {}",
                        request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), exception);
            }
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // 401 y 403 conservan sus tipos de dominio, para que sigan siendo errores distintos.
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return ApiErrorResponses.build(ApiErrorCatalog.of(ErrorType.NOT_AUTHENTICATED),
                    ErrorType.NOT_AUTHENTICATED.name(), "Debe iniciar sesión.", null, null, correlationId);
        }
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return ApiErrorResponses.build(ApiErrorCatalog.of(ErrorType.FORBIDDEN),
                    ErrorType.FORBIDDEN.name(), "No tiene permiso para esta operación.", null, null, correlationId);
        }
        HttpErrorType type = ApiErrorCatalog.forStatus(status);
        String detail = type == HttpErrorType.INTERNAL_ERROR
                ? "Ocurrió un error inesperado. Si persiste, informe el correlationId."
                : "La solicitud no se pudo atender.";
        return ApiErrorResponses.fromProtocol(type, detail, correlationId);
    }

    private static HttpStatusCode statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return code instanceof Integer value ? HttpStatusCode.valueOf(value) : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
