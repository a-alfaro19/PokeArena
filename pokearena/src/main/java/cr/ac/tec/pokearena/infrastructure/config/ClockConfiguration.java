package cr.ac.tec.pokearena.infrastructure.config;

import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.Clock;

/**
 * Propósito: implementación real del puerto {@link Clock} (regla 5 de la sección 4.2).
 * Responsabilidades: dar la hora del sistema. Es el único lugar de producción que llama a
 * {@code Instant.now()}; el dominio y la aplicación reciben el reloj por el puerto.
 * Referencia: sección 4.2, regla 5.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock systemClock() {
        return Instant::now;
    }
}
