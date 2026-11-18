package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.usecase.CreateCardUseCase;
import cr.ac.tec.pokearena.application.usecase.ExploreCatalogUseCase;
import cr.ac.tec.pokearena.domain.repository.PlayerCatalogRepository;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.application.usecase.ImageCreationSteps;
import cr.ac.tec.pokearena.application.usecase.Pagination;
import cr.ac.tec.pokearena.application.usecase.SearchCardsUseCase;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

/**
 * Propósito: ensamblar los casos de uso de cartas (historias 2.1.1, 2.1.2 y 3.1.2).
 * Responsabilidades: construir {@link CreateCardUseCase}, {@link SearchCardsUseCase} y
 * {@link ExploreCatalogUseCase}, que no llevan anotaciones de Spring (sección 4.3), con las
 * implementaciones de sus puertos.
 * Referencia: secciones 7.1, 7.3 y 8.3.
 *
 * <p>Cada puerto de cartas tiene su propio adaptador JDBC ({@code @Repository} de
 * {@code infrastructure.persistence}): {@code CardRepository} lo implementa {@code JdbcCardRepository},
 * {@code PlayerCatalogRepository} lo implementa {@code JdbcPlayerCatalogRepository} y
 * {@code CollectibleCardRepository} (colección inicial, en {@code CollectionConfiguration}) lo implementa
 * {@code JdbcCollectibleCardRepository}. Spring entrega cada uno por su interfaz.
 */
@Configuration
public class CardConfiguration {

    @Bean
    public CreateCardUseCase createCardUseCase(IdempotencyGuard idempotency, ImageCreationSteps imageCreation,
            CardCatalogRepository catalog, AbilityRepository abilities, CardRepository cards,
            ImageProcessor imageProcessor, ImageStorage imageStorage, Clock clock, CatalogCache cache) {
        return new CreateCardUseCase(idempotency, imageCreation, catalog, abilities, cards, imageProcessor,
                imageStorage, clock, cache);
    }

    @Bean
    public SearchCardsUseCase searchCardsUseCase(Pagination pagination, CardCatalogRepository catalog,
            CardRepository cards, CatalogCache cache) {
        return new SearchCardsUseCase(pagination, catalog, cards, cache);
    }
    /** Ensambla el catálogo sin introducir Spring en el caso de uso. */
    @Bean
    public ExploreCatalogUseCase exploreCatalogUseCase(Pagination pagination, CardCatalogRepository catalog,
            PlayerCatalogRepository cards, CatalogCache cache) {
        return new ExploreCatalogUseCase(pagination, catalog, cards, cache);
    }

}
