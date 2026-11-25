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

/**
 * Propósito: ensamblar los casos de uso de preparación y consulta de partidas.
 * Responsabilidades: conectar puertos, configuración y reglas con sus adaptadores.
 * Referencia: sección 7.3; Anexos A.14 y A.15.
 */
@Configuration
public class MatchConfiguration {

    @Bean
    public StartMatchUseCase startMatchUseCase(RandomSource random, Clock clock, LocationRepository locations,
            MatchRepository matches, MatchSettings settings, LocationEffectRegistry effects) {
        return new StartMatchUseCase(random, clock, locations, matches, settings, effects);
    }

    @Bean
    public GetMatchUseCase getMatchUseCase(MatchRepository matches, Clock clock) {
        return new GetMatchUseCase(matches, clock);
    }
}
