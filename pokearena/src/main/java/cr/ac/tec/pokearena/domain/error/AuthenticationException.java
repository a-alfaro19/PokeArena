package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: errores de identidad, cuando no se sabe quién hace la solicitud (secciones 5.2 y 8.1).
 * Responsabilidades: distinguir las credenciales propias ausentes o inválidas del token del
 * proveedor rechazado. Ambos son "no autenticado" y son distintos de {@link AuthorizationException}.
 * Referencia: sección 5.3, tipos NOT_AUTHENTICATED e INVALID_IDENTITY_TOKEN.
 */
public final class AuthenticationException extends DomainException {

    private AuthenticationException(ErrorType type, String message) {
        super(type, message);
    }

    /** Sin credenciales de PokéArena, o inválidas o vencidas. */
    public static AuthenticationException notAuthenticated(String message) {
        return new AuthenticationException(ErrorType.NOT_AUTHENTICATED, message);
    }

    /** Token del proveedor no emitido por él, vencido, con firma inválida o para otra aplicación. */
    public static AuthenticationException invalidIdentityToken(String message) {
        return new AuthenticationException(ErrorType.INVALID_IDENTITY_TOKEN, message);
    }
}
