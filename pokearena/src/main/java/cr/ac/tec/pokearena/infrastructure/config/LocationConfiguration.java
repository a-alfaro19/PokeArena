package cr.ac.tec.pokearena.infrastructure.config;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.EffectReader;
import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.usecase.CreateLocationUseCase;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.application.usecase.ImageCreationSteps;
import cr.ac.tec.pokearena.domain.effect.CostModifierEffect;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.LocationEffectType;
import cr.ac.tec.pokearena.domain.effect.TurnRestrictionEffect;
import cr.ac.tec.pokearena.domain.effect.TypeBonusEffect;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;

/**
 * Propósito: ensamblar Crear ubicación y sus plug-ins de efecto (historia 2.2.1).
 * Responsabilidades: declarar cada tipo de efecto como un bean, construir el registro con TODOS los
 * beans {@link LocationEffectType} que existan y construir el caso de uso.
 * Referencia: sección 7.2 y sección 4.4 (Strategy y Registry).
 *
 * <p>Cómo se agrega un efecto nuevo: se escribe su clase en {@code domain.effect} y se declara aquí
 * como un {@code @Bean}. Spring lo entrega al registro con los demás; ni el registro ni el caso de uso
 * cambian.
 */
@Configuration
public class LocationConfiguration {

    @Bean
    public TypeBonusEffect typeBonusEffect(CardCatalogRepository catalog) {
        return new TypeBonusEffect(existingTypes(catalog));
    }

    @Bean
    public CostModifierEffect costModifierEffect(CardCatalogRepository catalog) {
        return new CostModifierEffect(existingTypes(catalog));
    }

    @Bean
    public TurnRestrictionEffect turnRestrictionEffect() {
        return new TurnRestrictionEffect();
    }

    @Bean
    public LocationEffectRegistry locationEffectRegistry(List<LocationEffectType> effectTypes) {
        return new LocationEffectRegistry(effectTypes);
    }

    @Bean
    public CreateLocationUseCase createLocationUseCase(IdempotencyGuard idempotency, ImageCreationSteps imageCreation,
            EffectReader effectReader, LocationEffectRegistry effects, LocationRepository locations,
            ImageProcessor imageProcessor, ImageStorage imageStorage, Clock clock) {
        return new CreateLocationUseCase(idempotency, imageCreation, effectReader, effects, locations,
                imageProcessor, imageStorage, clock);
    }

    /** Tipos de carta del catálogo, leídos al validar: los plug-ins no tienen la lista fija. */
    private static Supplier<List<String>> existingTypes(CardCatalogRepository catalog) {
        return () -> catalog.findAllTypes().stream().map(CardType::code).toList();
    }
}
