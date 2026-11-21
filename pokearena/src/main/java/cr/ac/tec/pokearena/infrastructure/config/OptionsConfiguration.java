package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.usecase.GetCatalogOptionsUseCase;
import cr.ac.tec.pokearena.application.usecase.GetCreationOptionsUseCase;
import cr.ac.tec.pokearena.application.usecase.GetGameOptionsUseCase;
import cr.ac.tec.pokearena.application.usecase.Pagination;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;

/**
 * Propósito: ensamblar los casos de uso de las opciones de la interfaz.
 * Responsabilidades: construir {@link GetCatalogOptionsUseCase} y {@link GetCreationOptionsUseCase}, que no
 * llevan anotaciones de Spring (sección 4.3), con los mismos beans que usan las validaciones: la
 * paginación, los repositorios de catálogo y de habilidades, y el registro de efectos.
 * Referencia: {@code GET /api/v1/opciones/catalogo} y {@code GET /api/v1/opciones/creacion}.
 */
@Configuration
public class OptionsConfiguration {

    @Bean
    public GetCatalogOptionsUseCase getCatalogOptionsUseCase(CardCatalogRepository catalog, Pagination pagination) {
        return new GetCatalogOptionsUseCase(catalog, pagination);
    }

    @Bean
    public GetCreationOptionsUseCase getCreationOptionsUseCase(AbilityRepository abilities,
            LocationEffectRegistry effects) {
        return new GetCreationOptionsUseCase(abilities, effects);
    }

    /** Opciones del juego (Sprint 3, sección 7.4): por ahora solo lee constantes de {@code DeckRules}. */
    @Bean
    public GetGameOptionsUseCase getGameOptionsUseCase() {
        return new GetGameOptionsUseCase();
    }
}
