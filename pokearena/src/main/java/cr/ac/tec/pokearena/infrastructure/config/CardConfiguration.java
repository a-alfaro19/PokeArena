package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.CreateCardUseCase;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

/**
 * Propósito: ensamblar los casos de uso de cartas (historia 2.1.1).
 * Responsabilidades: construir {@link CreateCardUseCase}, que no lleva anotaciones de Spring
 * (sección 4.3), con las implementaciones de sus puertos.
 * Referencia: sección 7.1.
 */
@Configuration
public class CardConfiguration {

    @Bean
    public CreateCardUseCase createCardUseCase(IdempotencyGuard idempotency, CardCatalogRepository catalog,
            AbilityRepository abilities, CardRepository cards, ImageProcessor imageProcessor, ImageStorage imageStorage,
            UnitOfWork unitOfWork, Clock clock) {
        return new CreateCardUseCase(idempotency, catalog, abilities, cards, imageProcessor, imageStorage, unitOfWork,
                clock);
    }
}
