package cr.ac.tec.pokearena.api.error;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import cr.ac.tec.pokearena.api.error.ApiErrorCatalog.HttpErrorType;
import cr.ac.tec.pokearena.api.web.CorrelationIdFilter;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.IdempotencyException;
import cr.ac.tec.pokearena.domain.error.ImageValidationException;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Propósito: ÚNICO manejador global de errores de la API (sección 5.3, RNF-API-03).
 * Responsabilidades: convertir toda excepción que salga de un controlador al formato único
 * {@link ApiError}: los errores de dominio según su tipo, las excepciones estándar de Spring a su
 * tipo equivalente y cualquier otra excepción a {@code INTERNAL_ERROR} sin revelar su mensaje.
 * Referencia: secciones 5.3 y 5.6.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>No extiende {@code ResponseEntityExceptionHandler}: ese genera {@code ProblemDetail}, otro
 *       formato. Cada excepción de Spring se mapea aquí de forma explícita.</li>
 *   <li>Los mensajes de las excepciones de Spring nunca se copian al cuerpo (pueden repetir datos
 *       del cliente o detalles internos); el detalle siempre es un texto fijo en español.</li>
 *   <li>Los errores que ocurren fuera de los controladores (por ejemplo, en un filtro) no pasan por
 *       aquí: los atiende {@link ApiFallbackErrorController}, con el mismo formato.</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    // Límite configurado del archivo, para decirle al usuario cuánto es lo máximo.
    private final String maxFileSize;

    public ApiErrorHandler(@Value("${spring.servlet.multipart.max-file-size}") String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    // ---------------------------------------------------------------------------------------
    // Errores de dominio: el tipo, el mensaje, el campo y los valores permitidos ya vienen listos.
    // ---------------------------------------------------------------------------------------

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException exception, HttpServletRequest request) {
        LOG.info("Solicitud rechazada: {} {}", exception.type(), exception.field().orElse(""));
        return ApiErrorResponses.fromDomain(exception, CorrelationIdFilter.currentId(request));
    }

    // ---------------------------------------------------------------------------------------
    // Excepciones estándar de Spring traducidas a los tipos de la sección 5.3.
    // ---------------------------------------------------------------------------------------

    /** Falta un parámetro obligatorio de la URL o del formulario. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return handleDomain(ValidationException.missingField(exception.getParameterName(),
                "Falta el campo obligatorio '" + exception.getParameterName() + "'."), request);
    }

    /** Falta una parte obligatoria del formulario multipart, por ejemplo la imagen. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException exception,
            HttpServletRequest request) {
        return handleDomain(ValidationException.missingField(exception.getRequestPartName(),
                "Falta el campo obligatorio '" + exception.getRequestPartName() + "'."), request);
    }

    /** Falta un encabezado obligatorio. La clave de idempotencia tiene su propio tipo (sección 5.4). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException exception,
            HttpServletRequest request) {
        String header = exception.getHeaderName();
        DomainException error = IdempotencyException.IDEMPOTENCY_KEY_FIELD.equalsIgnoreCase(header)
                ? IdempotencyException.required("Falta el encabezado Idempotency-Key.")
                : ValidationException.missingField(header, "Falta el encabezado obligatorio '" + header + "'.");
        return handleDomain(error, request);
    }

    /** Un parámetro no tiene el tipo esperado (por ejemplo, texto donde va un número). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        Class<?> expected = exception.getRequiredType();
        // Si el parámetro es un catálogo (enum), se informan sus valores válidos.
        List<String> allowed = expected != null && expected.isEnum()
                ? Arrays.stream(expected.getEnumConstants()).map(Object::toString).toList()
                : List.of();
        return handleDomain(ValidationException.invalidField(exception.getName(),
                "El valor del campo '" + exception.getName() + "' no es válido.", allowed), request);
    }

    /** El archivo supera el límite del multipart: es el requisito de peso de la imagen. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return handleDomain(ImageValidationException.tooLarge(
                "La imagen supera el tamaño máximo permitido de " + maxFileSize + "."), request);
    }

    /** El cuerpo no se puede leer (JSON mal formado o de otra forma). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return protocol(HttpErrorType.BAD_REQUEST, "El cuerpo de la solicitud no tiene un formato válido.",
                request);
    }

    /** La ruta no existe. */
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<ApiError> handleNotFound(Exception exception, HttpServletRequest request) {
        return protocol(HttpErrorType.NOT_FOUND, "La ruta solicitada no existe.", request);
    }

    /** La ruta existe pero no admite ese método HTTP. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        String allowed = exception.getSupportedHttpMethods() == null ? ""
                : exception.getSupportedHttpMethods().stream().map(Object::toString)
                        .collect(Collectors.joining(", "));
        return ApiErrorResponses.withAllow(protocol(HttpErrorType.METHOD_NOT_ALLOWED,
                "La ruta no admite el método " + exception.getMethod() + ".", request), allowed);
    }

    /** El cuerpo viene en un tipo de contenido que la ruta no acepta. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return protocol(HttpErrorType.UNSUPPORTED_MEDIA_TYPE,
                "La ruta no acepta el tipo de contenido enviado.", request);
    }

    // ---------------------------------------------------------------------------------------
    // Todo lo demás.
    // ---------------------------------------------------------------------------------------

    /**
     * Cualquier otra excepción. Si es una excepción web de Spring con su propio status (por ejemplo,
     * 406), se respeta su familia; si no, es un error inesperado: 500 sin revelar el mensaje, que
     * queda en el log junto con el correlationId (el MDC ya lo tiene).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse springError && springError.getStatusCode().is4xxClientError()) {
            LOG.info("Solicitud rechazada por Spring: {}", exception.getClass().getSimpleName());
            return protocol(ApiErrorCatalog.forStatus(springError.getStatusCode()),
                    "La solicitud no es válida.", request);
        }
        LOG.error("Error inesperado al atender {} {}", request.getMethod(), request.getRequestURI(), exception);
        return protocol(HttpErrorType.INTERNAL_ERROR,
                "Ocurrió un error inesperado. Si persiste, informe el correlationId.", request);
    }

    private static ResponseEntity<ApiError> protocol(HttpErrorType type, String detail, HttpServletRequest request) {
        return ApiErrorResponses.fromProtocol(type, detail, CorrelationIdFilter.currentId(request));
    }
}
