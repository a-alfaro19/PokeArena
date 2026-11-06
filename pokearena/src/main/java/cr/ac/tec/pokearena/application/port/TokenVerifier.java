package cr.ac.tec.pokearena.application.port;

import cr.ac.tec.pokearena.domain.error.AuthenticationException;

/**
 * Propósito: puerto que verifica las credenciales propias de PokéArena (sección 5.2).
 * Responsabilidades: comprobar la firma y el vencimiento de un token y devolver quién es su dueño.
 * Nunca acepta el token del proveedor de identidad: solo credenciales emitidas por {@link TokenIssuer}.
 * Referencia: sección 5.2.
 */
public interface TokenVerifier {

    /**
     * Verifica {@code token}.
     *
     * @return el jugador y su rol, tal como se firmaron
     * @throws AuthenticationException NOT_AUTHENTICATED si el token no es válido, fue manipulado o venció
     */
    AuthenticatedUser verify(String token);
}
