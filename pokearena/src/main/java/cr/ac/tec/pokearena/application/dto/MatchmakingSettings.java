package cr.ac.tec.pokearena.application.dto;

import java.time.Duration;
import java.util.Objects;

/**
 * Propósito: valores configurables del emparejamiento (sección 5.7).
 * Responsabilidades: transportar intervalos, rangos y límites ya validados a la tarea y a los casos de uso,
 * sin acoplar la aplicación a propiedades de Spring.
 * Referencia: secciones 4.2 y 5.7.
 *
 * @param enabled si el emparejador programado está activo
 * @param tickInterval intervalo entre ejecuciones del emparejador
 * @param initialEloRange rango Elo inicial
 * @param eloRangeStep incremento del rango Elo
 * @param eloRangeStepInterval tiempo para cada incremento
 * @param maxEloRange rango Elo máximo
 * @param inactivityTimeout tiempo máximo sin consultar una búsqueda
 * @param botOfferAfter espera mínima para ofrecer jugar contra el bot
 * @param clientPollInterval intervalo recomendado de consulta al cliente
 * @param candidateBatchSize máximo de candidatas consultadas por pareja
 * @param maxPairsPerTick máximo de parejas procesadas por ejecución
 * @param failureRetryDelay espera antes de reintentar una preparación fallida
 */
public record MatchmakingSettings(boolean enabled, Duration tickInterval, int initialEloRange, int eloRangeStep,
        Duration eloRangeStepInterval, int maxEloRange, Duration inactivityTimeout, Duration botOfferAfter,
        Duration clientPollInterval, int candidateBatchSize, int maxPairsPerTick, Duration failureRetryDelay) {

    public MatchmakingSettings {
        Objects.requireNonNull(tickInterval, "El intervalo del emparejador es obligatorio");
        Objects.requireNonNull(eloRangeStepInterval, "El intervalo de rango Elo es obligatorio");
        Objects.requireNonNull(inactivityTimeout, "El tiempo de inactividad es obligatorio");
        Objects.requireNonNull(botOfferAfter, "La espera de oferta del bot es obligatoria");
        Objects.requireNonNull(clientPollInterval, "El intervalo de consulta es obligatorio");
        Objects.requireNonNull(failureRetryDelay, "La espera de reintento es obligatoria");
        if (!isPositive(tickInterval) || initialEloRange < 0 || eloRangeStep < 0
                || !isPositive(eloRangeStepInterval) || maxEloRange < initialEloRange
                || !isPositive(inactivityTimeout) || !isPositive(botOfferAfter)
                || !isPositive(clientPollInterval) || candidateBatchSize <= 0 || maxPairsPerTick <= 0
                || !isPositive(failureRetryDelay)) {
            throw new IllegalArgumentException("La configuración del emparejamiento no es válida.");
        }
    }

    private static boolean isPositive(Duration duration) {
        return !duration.isZero() && !duration.isNegative();
    }
}
