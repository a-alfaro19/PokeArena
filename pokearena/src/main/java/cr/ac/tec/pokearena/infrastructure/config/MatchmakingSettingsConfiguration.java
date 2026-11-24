package cr.ac.tec.pokearena.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.dto.MatchmakingSettings;

/**
 * Propósito: leer y validar la configuración del emparejamiento (sección 5.7).
 * Responsabilidades: construir {@link MatchmakingSettings} para las tareas y casos de uso, fuera del dominio.
 * Referencia: secciones 4.2 y 5.7.
 */
@Configuration
public class MatchmakingSettingsConfiguration {

    @Bean
    public MatchmakingSettings matchmakingSettings(
            @Value("${app.matchmaking.enabled}") boolean enabled,
            @Value("${app.matchmaking.tick-interval}") Duration tickInterval,
            @Value("${app.matchmaking.initial-elo-range}") int initialEloRange,
            @Value("${app.matchmaking.elo-range-step}") int eloRangeStep,
            @Value("${app.matchmaking.elo-range-step-interval}") Duration eloRangeStepInterval,
            @Value("${app.matchmaking.max-elo-range}") int maxEloRange,
            @Value("${app.matchmaking.inactivity-timeout}") Duration inactivityTimeout,
            @Value("${app.matchmaking.bot-offer-after}") Duration botOfferAfter,
            @Value("${app.matchmaking.client-poll-interval}") Duration clientPollInterval,
            @Value("${app.matchmaking.candidate-batch-size}") int candidateBatchSize,
            @Value("${app.matchmaking.max-pairs-per-tick}") int maxPairsPerTick,
            @Value("${app.matchmaking.failure-retry-delay}") Duration failureRetryDelay) {
        return new MatchmakingSettings(enabled, tickInterval, initialEloRange, eloRangeStep, eloRangeStepInterval,
                maxEloRange, inactivityTimeout, botOfferAfter, clientPollInterval, candidateBatchSize, maxPairsPerTick,
                failureRetryDelay);
    }
}
