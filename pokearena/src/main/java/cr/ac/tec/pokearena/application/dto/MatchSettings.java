package cr.ac.tec.pokearena.application.dto;

import java.time.Duration;
import java.util.Objects;

/**
 * Propósito: transportar las reglas configurables de una partida.
 * Responsabilidades: validar límites de preparación y entregar valores inmutables a los casos de uso.
 * Referencia: secciones 4.2 y 5.7; historia 5.1.1.
 */
public record MatchSettings(int locationsPerMatch, int minPlayableLocations, int initialHandSize, int maxHandSize,
        int initialEnergy, int energyPerTurn, int maxTurns, Duration turnDuration, int initialBet) {

    public MatchSettings {
        Objects.requireNonNull(turnDuration, "La duración del turno es obligatoria");
        if (locationsPerMatch <= 0 || minPlayableLocations <= 0 || minPlayableLocations > locationsPerMatch
                || initialHandSize <= 0 || maxHandSize <= initialHandSize || initialEnergy < 0 || energyPerTurn < 0
                || maxTurns <= 0 || turnDuration.isZero() || turnDuration.isNegative() || initialBet < 0) {
            throw new IllegalArgumentException("La configuración de la partida no es válida.");
        }
    }
}
