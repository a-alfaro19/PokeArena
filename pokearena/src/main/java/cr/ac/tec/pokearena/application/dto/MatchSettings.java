package cr.ac.tec.pokearena.application.dto;

import java.time.Duration;
import java.util.Objects;

/**
 * Propósito: valores configurables de las reglas de una partida (sección 5.7).
 * Responsabilidades: transportar la configuración validada desde infraestructura hacia las reglas y casos de
 * uso, sin que el dominio lea propiedades de Spring.
 * Referencia: secciones 4.2 y 5.7.
 *
 * @param locationsPerMatch cantidad de ubicaciones seleccionadas
 * @param minPlayableLocations mínimo de ubicaciones donde se puede jugar
 * @param initialHandSize cantidad de cartas repartidas antes del robo inicial
 * @param maxHandSize máximo de cartas en mano
 * @param initialEnergy energía base del primer turno
 * @param energyPerTurn incremento de energía por turno
 * @param maxTurns cantidad máxima de turnos
 * @param turnDuration duración de cada turno
 * @param initialBet apuesta inicial
 * @param maxBet apuesta máxima posible
 */
public record MatchSettings(int locationsPerMatch, int minPlayableLocations, int initialHandSize, int maxHandSize,
        int initialEnergy, int energyPerTurn, int maxTurns, Duration turnDuration, int initialBet, int maxBet) {

    public MatchSettings {
        Objects.requireNonNull(turnDuration, "La duración del turno es obligatoria");
        if (locationsPerMatch <= 0 || minPlayableLocations <= 0 || minPlayableLocations > locationsPerMatch
                || initialHandSize < 0 || maxHandSize <= 0 || initialHandSize > maxHandSize
                || initialEnergy < 0 || energyPerTurn < 0 || maxTurns <= 0
                || turnDuration.isZero() || turnDuration.isNegative()
                || initialBet < 0 || maxBet < initialBet) {
            throw new IllegalArgumentException("La configuración de la partida no es válida.");
        }
    }
}
