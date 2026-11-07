package cr.ac.tec.pokearena.application.dto;

import cr.ac.tec.pokearena.domain.error.IdentityProviderException;

/**
 * Propósito: datos del inicio de sesión con proveedor (sección 8.1, paso 1).
 * Responsabilidades: llevar al caso de uso, tal como llegaron, el nombre del proveedor y su token. Los
 * valores pueden faltar: el caso de uso los valida con su error tipado.
 * Referencia: sección 8.1 y diagrama A.4 (mensaje 5).
 *
 * @param provider nombre del proveedor, por ejemplo {@code google}
 * @param idToken  token de identidad que el proveedor entregó a la interfaz
 */
public record LoginCommand(String provider, String idToken) {

    /** Nombre del campo del proveedor, para los errores. */
    public static final String PROVIDER_FIELD = IdentityProviderException.PROVIDER_FIELD;

    /** Nombre del campo del token, para los errores. */
    public static final String ID_TOKEN_FIELD = "idToken";

    /** El token nunca aparece en logs ni en mensajes de error. */
    @Override
    public String toString() {
        return "LoginCommand[provider=" + provider + "]";
    }
}
