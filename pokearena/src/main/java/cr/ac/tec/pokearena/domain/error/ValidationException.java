package cr.ac.tec.pokearena.domain.error;

import java.util.List;

/**
 * Propósito: errores de validación de campos, reglas y criterios (sección 5.3).
 * Responsabilidades: ofrecer una fábrica por cada tipo de validación, con el campo afectado y
 * sus valores permitidos cuando aplican.
 * Referencia: sección 5.3, tipos INVALID_FIELD, MISSING_FIELD, UNKNOWN_ABILITY,
 * INVALID_EVOLUTION, INVALID_EFFECT, INVALID_SEARCH_CRITERIA e INVALID_PAGINATION.
 */
public final class ValidationException extends DomainException {

    private ValidationException(ErrorType type, String message, String field, List<String> allowedValues) {
        super(type, message, field, allowedValues);
    }

    /** Valor fuera de rango o de catálogo; por ejemplo {@code invalidField("cost", "...", List.of("0..10"))}. */
    public static ValidationException invalidField(String field, String message, List<String> allowedValues) {
        return new ValidationException(ErrorType.INVALID_FIELD, message, field, allowedValues);
    }

    /** Falta un campo obligatorio, por ejemplo la habilidad. */
    public static ValidationException missingField(String field, String message) {
        return new ValidationException(ErrorType.MISSING_FIELD, message, field, null);
    }

    /** El código de habilidad no existe. */
    public static ValidationException unknownAbility(String field, String message) {
        return new ValidationException(ErrorType.UNKNOWN_ABILITY, message, field, null);
    }

    /** Evolución a la misma carta o que forma un ciclo. */
    public static ValidationException invalidEvolution(String field, String message) {
        return new ValidationException(ErrorType.INVALID_EVOLUTION, message, field, null);
    }

    /** Tipo de efecto inexistente o parámetros inválidos; puede indicar los efectos permitidos. */
    public static ValidationException invalidEffect(String field, String message, List<String> allowedValues) {
        return new ValidationException(ErrorType.INVALID_EFFECT, message, field, allowedValues);
    }

    /** Criterio de búsqueda inválido; {@code field} es el criterio. */
    public static ValidationException invalidSearchCriteria(String criterion, String message,
            List<String> allowedValues) {
        return new ValidationException(ErrorType.INVALID_SEARCH_CRITERIA, message, criterion, allowedValues);
    }

    /** {@code limit} o {@code cursor} inválidos. */
    public static ValidationException invalidPagination(String field, String message, List<String> allowedValues) {
        return new ValidationException(ErrorType.INVALID_PAGINATION, message, field, allowedValues);
    }
}
