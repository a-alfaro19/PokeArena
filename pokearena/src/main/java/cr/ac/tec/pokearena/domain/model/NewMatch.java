package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.rule.TurnStartRules;

/**
 * Propósito: agregado inicial de partida listo para persistirse.
 * Responsabilidades: proteger sus invariantes antes de que MatchRepository guarde cualquier fila.
 * Referencia: sección 6.2; sección 7.3, pasos 5 y 6; Anexo A.14.
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
        if (status != MatchStatus.ACTIVE || turn != TurnStartRules.FIRST_TURN || bet < 0 || turnDurationSeconds <= 0) {
            throw new IllegalArgumentException("El estado inicial de la partida no es válido");
        }
        validateLocations(locations);
        validateParticipants(participants, vsBot);
    }

    private static void validateLocations(List<MatchLocation> locations) {
        Set<Integer> slots = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        for (MatchLocation location : locations) {
            Objects.requireNonNull(location, "Cada espacio debe tener una ubicación");
            if (!slots.add(location.slot()) || !locationIds.add(location.locationId())) {
                throw new IllegalArgumentException("Los espacios y las ubicaciones de la partida deben ser distintos");
            }
        }
        if (locations.size() != MatchLocation.LAST_SLOT || slots.size() != MatchLocation.LAST_SLOT) {
            throw new IllegalArgumentException("La partida necesita tres ubicaciones distintas");
        }
    }

    private static void validateParticipants(List<MatchParticipant> participants, boolean vsBot) {
        Set<Integer> seats = new HashSet<>();
        Set<UUID> playerIds = new HashSet<>();
        int botCount = 0;
        for (MatchParticipant participant : participants) {
            Objects.requireNonNull(participant, "Cada asiento debe tener un participante");
            if (!seats.add(participant.seat()) || !participant.active()) {
                throw new IllegalArgumentException("Los dos asientos iniciales deben ser distintos y activos");
            }
            if (participant.isBot()) {
                botCount++;
            } else if (!playerIds.add(participant.playerId())) {
                throw new IllegalArgumentException("Un jugador no puede ocupar ambos asientos");
            }
        }
        if (participants.size() != MatchParticipant.LAST_SEAT || seats.size() != MatchParticipant.LAST_SEAT
                || vsBot != (botCount == 1)) {
            throw new IllegalArgumentException("La partida necesita dos asientos y un contexto de bot consistente");
        }
    }
}
