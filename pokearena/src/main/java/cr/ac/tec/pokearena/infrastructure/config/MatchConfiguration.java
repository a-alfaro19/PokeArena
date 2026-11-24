package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.dto.MatchSettings;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.RandomSource;
import cr.ac.tec.pokearena.application.usecase.GetMatchUseCase;
import cr.ac.tec.pokearena.application.usecase.StartMatchUseCase;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.LocationSelectionRules;
import cr.ac.tec.pokearena.domain.rule.MatchView;
import cr.ac.tec.pokearena.domain.rule.TurnStartRules;

/**
 * Propósito: ensamblar los casos de uso y reglas de la historia 5.1.1.
 * Responsabilidades: construir reglas puras con {@link MatchSettings} y conectar puertos de aplicación con
 * repositorios, reloj y fuente de aleatoriedad.
 * Referencia: secciones 4.3 y 7.3; Anexos A.14 y A.15.
 */
@Configuration
public class MatchConfiguration {

    @Bean
    public LocationSelectionRules locationSelectionRules(MatchSettings settings, LocationEffectRegistry effects) {
        return new LocationSelectionRules(settings.locationsPerMatch(), settings.minPlayableLocations(), effects);
    }

    @Bean
    public TurnStartRules turnStartRules(MatchSettings settings) {
        return new TurnStartRules(settings.initialHandSize(), settings.maxHandSize(), settings.initialEnergy(),
                settings.energyPerTurn(), settings.maxTurns());
    }

    @Bean
    public MatchView matchView() {
        return new MatchView();
    }

    @Bean
    public StartMatchUseCase startMatchUseCase(RandomSource random, Clock clock, LocationRepository locations,
            MatchRepository matches, MatchSettings settings, LocationSelectionRules locationSelection,
            TurnStartRules turnStart) {
        return new StartMatchUseCase(random, clock, locations, matches, settings, locationSelection, turnStart);
    }

    @Bean
    public GetMatchUseCase getMatchUseCase(MatchRepository matches, Clock clock, MatchView view) {
        return new GetMatchUseCase(matches, clock, view);
    }
}
