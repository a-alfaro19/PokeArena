package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.IdempotencyStore;
import cr.ac.tec.pokearena.application.port.ResponseSerializer;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;

/**
 * Propósito: ensamblar la idempotencia de los POST de creación (sección 5.4).
 * Responsabilidades: construir {@link IdempotencyGuard}, que no lleva anotaciones de Spring
 * (sección 4.3), con las implementaciones de sus puertos.
 * Referencia: sección 5.4.
 */
@Configuration
public class IdempotencyConfiguration {

    @Bean
    public IdempotencyGuard idempotencyGuard(IdempotencyStore store, ResponseSerializer serializer) {
        return new IdempotencyGuard(store, serializer);
    }
}
