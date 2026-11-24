package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Propósito: datos completos para guardar una partida recién preparada, antes de asignarle un id.
 * Responsabilidades: conservar semilla, estado de turno, ubicaciones y asientos para que el repositorio los
 * guarde como una sola unidad.
 * Referencia: sección 7.3, paso 6; Anexo A.14, mensaje 20.
 *
 * @param context origen de la partida
 * @param vsBot indica si uno de los asientos corresponde al bot
 * @param status estado inicial de la partida
 * @param seed semilla que permite reproducir las selecciones
 * @param bet apuesta registrada
 * @param turn turno actual
 * @param turnStartedAt hora de inicio del turno
 * @param turnDurationSeconds duración del turno
 * @param createdAt hora de creación de la partida
 * @param locations ubicaciones asignadas en orden de espacio
 * @param participants estado inicial de los dos asientos
 */
public record NewMatch(MatchContext context, boolean vsBot, MatchStatus status, long seed, int bet, int turn,
        Instant turnStartedAt, int turnDurationSeconds, Instant createdAt, List<MatchLocation> locations,
        List<MatchParticipant> participants) {

    public NewMatch {
        Objects.requireNonNull(context, "El contexto es obligatorio");
        Objects.requireNonNull(status, "El estado es obligatorio");
        Objects.requireNonNull(turnStartedAt, "La hora de inicio del turno es obligatoria");
        Objects.requireNonNull(createdAt, "La hora de creación es obligatoria");
        locations = List.copyOf(locations);
        participants = List.copyOf(participants);
        if (bet < 0 || turn < Match.FIRST_TURN || turnDurationSeconds <= 0) {
            throw new IllegalArgumentException("La apuesta, el turno o su duración no cumplen sus invariantes.");
        }
        if (participants.size() != MatchSeat.SEAT_COUNT
                || participants.stream().map(MatchParticipant::seat).distinct().count() != participants.size()
                || participants.stream().map(MatchParticipant::seat).min(Integer::compareTo).orElse(0)
                        != MatchSeat.FIRST_SEAT
                || participants.stream().map(MatchParticipant::seat).max(Integer::compareTo).orElse(0)
                        != MatchSeat.SECOND_SEAT) {
            throw new IllegalArgumentException("La partida debe tener un participante en cada asiento.");
        }
        long bots = participants.stream().filter(MatchParticipant::isBot).count();
        if ((vsBot && bots != 1) || (!vsBot && bots != 0)) {
            throw new IllegalArgumentException("La marca vsBot debe coincidir con los participantes.");
        }
        if (locations.stream().map(MatchLocation::id).distinct().count() != locations.size()
                || locations.stream().map(MatchLocation::slot).distinct().count() != locations.size()) {
            throw new IllegalArgumentException("Las ubicaciones deben tener ids y espacios distintos.");
        }
    }
}
