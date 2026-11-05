package cr.ac.tec.pokearena.domain.error;

import java.util.List;

/**
 * Propósito: errores del proveedor de identidad externo (sección 8.1).
 * Responsabilidades: distinguir un proveedor no registrado de uno que no responde.
 * Referencia: sección 5.3, tipos UNSUPPORTED_PROVIDER e IDENTITY_PROVIDER_UNAVAILABLE.
 */
public final class IdentityProviderException extends DomainException {

    /** Nombre del campo del proveedor en {@code POST /api/v1/sesiones}. */
    public static final String PROVIDER_FIELD = "provider";

    private IdentityProviderException(ErrorType type, String message, String field, List<String> allowedValues) {
        super(type, message, field, allowedValues);
    }

    /** Proveedor no registrado; {@code allowedValues} son los proveedores registrados. */
    public static IdentityProviderException unsupported(String message, List<String> allowedValues) {
        return new IdentityProviderException(ErrorType.UNSUPPORTED_PROVIDER, message, PROVIDER_FIELD, allowedValues);
    }

    /** El proveedor no responde; no se crea ninguna cuenta. */
    public static IdentityProviderException unavailable(String message) {
        return new IdentityProviderException(ErrorType.IDENTITY_PROVIDER_UNAVAILABLE, message, null, null);
    }
}
