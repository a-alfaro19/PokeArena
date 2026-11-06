package cr.ac.tec.pokearena.support;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Propósito: secretos aleatorios para las pruebas.
 * Responsabilidades: generar secretos JWT válidos sin escribir ninguno en el repositorio.
 * Referencia: sección 5.2 y sección 12 ("ningún secreto en el repositorio").
 */
public final class TestSecrets {

    // 48 bytes aleatorios, en Base64: más de los 32 que exige HS256.
    private static final int RANDOM_BYTES = 48;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TestSecrets() {
    }

    public static String randomJwtSecret() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
