package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: error de un usuario autenticado que no tiene el rol requerido (sección 5.2).
 * Responsabilidades: representar el 403, separado del "no autenticado" (401), como piden los
 * criterios de las historias de administrador.
 * Referencia: sección 5.3, tipo FORBIDDEN.
 */
public final class AuthorizationException extends DomainException {

    private AuthorizationException(String message) {
        super(ErrorType.FORBIDDEN, message);
    }

    public static AuthorizationException forbidden(String message) {
        return new AuthorizationException(message);
    }
}
