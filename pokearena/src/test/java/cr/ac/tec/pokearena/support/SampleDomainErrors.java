package cr.ac.tec.pokearena.support;

import java.util.List;

import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.error.AuthorizationException;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyException;
import cr.ac.tec.pokearena.domain.error.IdentityProviderException;
import cr.ac.tec.pokearena.domain.error.ImageValidationException;
import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: ejemplos de errores de dominio para las pruebas, uno por cada tipo de la sección 5.3.
 * Responsabilidades: crear cada excepción con su fábrica real. El {@code switch} es exhaustivo:
 * si se agrega un tipo al dominio, las pruebas no compilan hasta tener su ejemplo.
 * Referencia: sección 5.3.
 */
public final class SampleDomainErrors {

    private SampleDomainErrors() {
    }

    public static DomainException of(ErrorType type) {
        return switch (type) {
            case NOT_AUTHENTICATED -> AuthenticationException.notAuthenticated("Debe iniciar sesión.");
            case FORBIDDEN -> AuthorizationException.forbidden("Se requiere el rol de administrador.");
            case INVALID_IDENTITY_TOKEN ->
                    AuthenticationException.invalidIdentityToken("El token del proveedor no es válido.");
            case IDENTITY_PROVIDER_UNAVAILABLE ->
                    IdentityProviderException.unavailable("El proveedor de identidad no responde.");
            case UNSUPPORTED_PROVIDER ->
                    IdentityProviderException.unsupported("El proveedor no está soportado.", List.of("google"));
            case INVALID_FIELD ->
                    ValidationException.invalidField("cost", "El costo debe estar entre 0 y 10.", List.of("0..10"));
            case MISSING_FIELD -> ValidationException.missingField("ability", "Debe elegir una habilidad.");
            case UNKNOWN_ABILITY -> ValidationException.unknownAbility("ability", "La habilidad HAB-999 no existe.");
            case INVALID_EVOLUTION ->
                    ValidationException.invalidEvolution("ability", "Una carta no puede evolucionar a sí misma.");
            case INVALID_EFFECT -> ValidationException.invalidEffect("effectType", "El efecto no existe.",
                    List.of("TYPE_BONUS", "COST_MODIFIER", "TURN_RESTRICTION"));
            case INVALID_SEARCH_CRITERIA -> ValidationException.invalidSearchCriteria("costMin",
                    "El mínimo no puede superar el máximo.", List.of());
            case INVALID_PAGINATION ->
                    ValidationException.invalidPagination("limit", "El límite debe estar entre 1 y 100.",
                            List.of("1..100"));
            case INVALID_IMAGE_FORMAT -> ImageValidationException.invalidFormat("La imagen debe ser PNG, JPG o WebP.");
            case IMAGE_TOO_LARGE -> ImageValidationException.tooLarge("La imagen supera los 5 MB.");
            case INVALID_IMAGE_RATIO -> ImageValidationException.invalidRatio("La imagen debe tener proporción 5:7.");
            case IMAGE_RESOLUTION_TOO_LOW ->
                    ImageValidationException.resolutionTooLow("La imagen debe medir al menos 600 × 840.");
            case IDEMPOTENCY_KEY_REQUIRED -> IdempotencyException.required("Falta el encabezado Idempotency-Key.");
            case IDEMPOTENCY_KEY_REUSED ->
                    IdempotencyException.reused("La clave ya se usó con un contenido distinto.");
        };
    }
}
