package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: catálogo cerrado de los tipos de error de dominio de la tabla de la sección 5.3.
 * Responsabilidades: nombrar cada error con el mismo código que viaja en el campo {@code type}
 * de la respuesta. No conoce códigos HTTP: la traducción a status vive en
 * {@code api.error.ApiErrorHandler}, así el dominio sigue sin depender de la web (regla 3 de 4.2).
 * Referencia: sección 5.3.
 */
public enum ErrorType {
    // Autenticación y autorización (sección 5.2).
    NOT_AUTHENTICATED,
    FORBIDDEN,

    // Inicio de sesión con proveedor (sección 8.1).
    INVALID_IDENTITY_TOKEN,
    IDENTITY_PROVIDER_UNAVAILABLE,
    UNSUPPORTED_PROVIDER,

    // Validación de campos y reglas.
    INVALID_FIELD,
    MISSING_FIELD,
    UNKNOWN_ABILITY,
    INVALID_EVOLUTION,
    INVALID_EFFECT,
    INVALID_SEARCH_CRITERIA,
    INVALID_PAGINATION,

    // Imágenes (secciones 7.1 y 7.2).
    INVALID_IMAGE_FORMAT,
    IMAGE_TOO_LARGE,
    INVALID_IMAGE_RATIO,
    IMAGE_RESOLUTION_TOO_LOW,

    // Idempotencia (sección 5.4).
    IDEMPOTENCY_KEY_REQUIRED,
    IDEMPOTENCY_KEY_REUSED
}
