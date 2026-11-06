package cr.ac.tec.pokearena.infrastructure.identity;

/**
 * Propósito: error de configuración del secreto de firma de las credenciales (sección 5.2).
 * Responsabilidades: impedir que la aplicación arranque sin un secreto válido en
 * {@code APP_JWT_SECRET}. {@link JwtSecretFailureAnalyzer} la convierte en un mensaje claro.
 * Referencia: sección 5.2 ("El secreto de firma viene de APP_JWT_SECRET").
 */
public class InvalidJwtSecretException extends RuntimeException {

    public InvalidJwtSecretException(String message) {
        super(message);
    }
}
