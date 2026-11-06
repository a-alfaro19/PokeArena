package cr.ac.tec.pokearena.application.port;

import java.time.Instant;

/**
 * Propósito: puerto del reloj (regla 5 de la sección 4.2).
 * Responsabilidades: dar la hora actual a la aplicación sin llamar a {@code Instant.now()}
 * directamente, para que las pruebas puedan usar un reloj fijo (por ejemplo, para un token vencido).
 * Referencia: sección 4.2, regla 5.
 */
@FunctionalInterface
public interface Clock {

    /** Momento actual. */
    Instant now();
}
