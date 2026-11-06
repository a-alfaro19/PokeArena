package cr.ac.tec.pokearena.application.port;

import java.time.Duration;
import java.time.Instant;

/**
 * Propósito: puerto que emite las credenciales propias de PokéArena (sección 5.2; emisor de
 * credenciales del Anexo A.4).
 * Responsabilidades: crear un token firmado con el id del jugador ({@code sub}), su rol
 * ({@code role}) y un vencimiento corto. La implementación vive en {@code infrastructure.identity}.
 * Referencia: sección 5.2.
 */
public interface TokenIssuer {

    /** Emite las credenciales de {@code user}. */
    IssuedToken issue(AuthenticatedUser user);

    /**
     * Credenciales emitidas.
     *
     * @param value     token para el encabezado {@code Authorization: Bearer}
     * @param validity  duración de su validez, para informar {@code expiresIn}
     * @param expiresAt momento en que vence
     */
    record IssuedToken(String value, Duration validity, Instant expiresAt) {
    }
}
