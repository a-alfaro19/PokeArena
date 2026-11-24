package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: partida persistida con sus datos visibles para el dominio.
 * Responsabilidades: reunir estado, ubicaciones, participantes, cartas y fichas de catálogo necesarias para
 * construir una vista por asiento sin consultar otros repositorios.
 * Referencia: secciones 6.2 y 7.3; Anexo A.15.
 *
 * @param id identificador de la partida
 * @param context origen de la partida
 * @param vsBot indica si participa el bot
 * @param status estado de la partida
 * @param seed semilla privada para reproducibilidad
 * @param bet apuesta registrada
 * @param turn turno actual
 * @param turnStartedAt hora de inicio del turno actual
 * @param turnDurationSeconds duración del turno
 * @param createdAt hora de creación
 * @param locations ubicaciones en orden de espacio
 * @param participants participantes en orden de asiento
 * @param cardsByCode fichas de catálogo para las cartas de la partida
 */
public record Match(UUID id, MatchContext context, boolean vsBot, MatchStatus status, long seed, int bet, int turn,
        Instant turnStartedAt, int turnDurationSeconds, Instant createdAt, List<MatchLocation> locations,
        List<MatchParticipant> participants, Map<String, Card> cardsByCode) {

    public static final int FIRST_TURN = 1;

    public Match {
        Objects.requireNonNull(id, "El id de la partida es obligatorio");
        Objects.requireNonNull(context, "El contexto es obligatorio");
        Objects.requireNonNull(status, "El estado es obligatorio");
        Objects.requireNonNull(turnStartedAt, "La hora de inicio del turno es obligatoria");
        Objects.requireNonNull(createdAt, "La hora de creación es obligatoria");
        locations = List.copyOf(locations);
        participants = List.copyOf(participants);
        cardsByCode = Map.copyOf(cardsByCode);
    }
}
