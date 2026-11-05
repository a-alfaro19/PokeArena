package cr.ac.tec.pokearena.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import cr.ac.tec.pokearena.domain.error.ErrorType;

/**
 * Propósito: traducir cada tipo de error a su status HTTP y su título en español (sección 5.3).
 * Responsabilidades: ser el ÚNICO lugar donde un tipo de error se asocia a un status. Lo usan el
 * manejador global y el controlador de {@code /error}, así ambos responden igual.
 * Referencia: tabla de la sección 5.3.
 *
 * <p>Hay dos grupos de tipos:
 * <ul>
 *   <li>Los de dominio ({@link ErrorType}), que lanzan los casos de uso. El {@code switch} es
 *       exhaustivo: si se agrega un tipo nuevo al dominio, esto no compila hasta asignarle status.</li>
 *   <li>Los de protocolo ({@link HttpErrorType}), que no vienen del dominio sino de HTTP: ruta
 *       inexistente, método no permitido, cuerpo ilegible o error inesperado.</li>
 * </ul>
 */
final class ApiErrorCatalog {

    /** Status y título de un tipo de error. */
    record Entry(HttpStatus status, String title) {
    }

    /** Tipos de error que nacen en el protocolo HTTP, no en el dominio. */
    enum HttpErrorType {
        BAD_REQUEST(HttpStatus.BAD_REQUEST, "Solicitud inválida"),
        NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso no encontrado"),
        METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido"),
        UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado"),
        INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");

        private final Entry entry;

        HttpErrorType(HttpStatus status, String title) {
            this.entry = new Entry(status, title);
        }

        Entry entry() {
            return entry;
        }
    }

    private ApiErrorCatalog() {
    }

    static Entry of(ErrorType type) {
        return switch (type) {
            case NOT_AUTHENTICATED -> new Entry(HttpStatus.UNAUTHORIZED, "No autenticado");
            case FORBIDDEN -> new Entry(HttpStatus.FORBIDDEN, "Sin permiso");
            case INVALID_IDENTITY_TOKEN -> new Entry(HttpStatus.UNAUTHORIZED, "Token de identidad inválido");
            case IDENTITY_PROVIDER_UNAVAILABLE ->
                    new Entry(HttpStatus.SERVICE_UNAVAILABLE, "Proveedor de identidad no disponible");
            case UNSUPPORTED_PROVIDER -> new Entry(HttpStatus.BAD_REQUEST, "Proveedor no soportado");
            case INVALID_FIELD -> new Entry(HttpStatus.BAD_REQUEST, "Campo inválido");
            case MISSING_FIELD -> new Entry(HttpStatus.BAD_REQUEST, "Campo obligatorio");
            case UNKNOWN_ABILITY -> new Entry(HttpStatus.BAD_REQUEST, "Habilidad inexistente");
            case INVALID_EVOLUTION -> new Entry(HttpStatus.BAD_REQUEST, "Evolución inválida");
            case INVALID_EFFECT -> new Entry(HttpStatus.BAD_REQUEST, "Efecto inválido");
            case INVALID_SEARCH_CRITERIA -> new Entry(HttpStatus.BAD_REQUEST, "Criterio de búsqueda inválido");
            case INVALID_PAGINATION -> new Entry(HttpStatus.BAD_REQUEST, "Paginación inválida");
            case INVALID_IMAGE_FORMAT -> new Entry(HttpStatus.BAD_REQUEST, "Formato de imagen no permitido");
            case IMAGE_TOO_LARGE -> new Entry(HttpStatus.BAD_REQUEST, "Imagen demasiado grande");
            case INVALID_IMAGE_RATIO -> new Entry(HttpStatus.BAD_REQUEST, "Proporción de imagen incorrecta");
            case IMAGE_RESOLUTION_TOO_LOW -> new Entry(HttpStatus.BAD_REQUEST, "Resolución de imagen insuficiente");
            case IDEMPOTENCY_KEY_REQUIRED -> new Entry(HttpStatus.BAD_REQUEST, "Falta la clave de idempotencia");
            case IDEMPOTENCY_KEY_REUSED ->
                    new Entry(HttpStatus.UNPROCESSABLE_CONTENT, "Clave de idempotencia reutilizada");
        };
    }

    /**
     * Tipo de protocolo para un status que llega sin excepción de dominio (por ejemplo, una
     * excepción estándar de Spring o un error despachado a {@code /error}). Los 4xx sin tipo
     * propio se informan como 400 y cualquier 5xx como 500, sin revelar la causa.
     */
    static HttpErrorType forStatus(HttpStatusCode status) {
        for (HttpErrorType type : HttpErrorType.values()) {
            if (type.entry().status().isSameCodeAs(status)) {
                return type;
            }
        }
        return status.is4xxClientError() ? HttpErrorType.BAD_REQUEST : HttpErrorType.INTERNAL_ERROR;
    }
}
