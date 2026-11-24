package cr.ac.tec.pokearena.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.dto.MatchSettings;

/**
 * Propósito: leer y validar la configuración de las partidas (sección 5.7).
 * Responsabilidades: construir {@link MatchSettings} para inyectar valores externos a las reglas de dominio.
 * Referencia: secciones 4.2 y 5.7.
 */
@Configuration
public class MatchSettingsConfiguration {

    @Bean
    public MatchSettings matchSettings(
            @Value("${app.match.locations-per-match}") int locationsPerMatch,
            @Value("${app.match.min-playable-locations}") int minPlayableLocations,
            @Value("${app.match.initial-hand-size}") int initialHandSize,
            @Value("${app.match.max-hand-size}") int maxHandSize,
            @Value("${app.match.initial-energy}") int initialEnergy,
            @Value("${app.match.energy-per-turn}") int energyPerTurn,
            @Value("${app.match.max-turns}") int maxTurns,
            @Value("${app.match.turn-duration}") Duration turnDuration,
            @Value("${app.match.initial-bet}") int initialBet,
            @Value("${app.match.max-bet}") int maxBet) {
        return new MatchSettings(locationsPerMatch, minPlayableLocations, initialHandSize, maxHandSize, initialEnergy,
                energyPerTurn, maxTurns, turnDuration, initialBet, maxBet);
    }
}
