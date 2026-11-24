package cr.ac.tec.pokearena.application.dto;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSeat;

/**
 * Propósito: datos de los dos asientos que entrega quien forma una pareja.
 * Responsabilidades: transportar contexto y decks previamente validados a {@code StartMatchUseCase}.
 * Referencia: sección 7.3 y Anexo A.14, mensaje 1.
 *
 * @param context origen de la partida
 * @param seats participantes y códigos de sus decks
 */
public record MatchRequest(MatchContext context, List<MatchSeat> seats) {

    public MatchRequest {
        Objects.requireNonNull(context, "El contexto de la partida es obligatorio");
        seats = seats.stream().sorted(Comparator.comparingInt(MatchSeat::seat)).toList();
        if (seats.size() != MatchSeat.SEAT_COUNT
                || seats.getFirst().seat() != MatchSeat.FIRST_SEAT
                || seats.getLast().seat() != MatchSeat.SECOND_SEAT) {
            throw new IllegalArgumentException("La solicitud debe contener los dos asientos de la partida.");
        }
    }

    /** Si alguno de los dos asientos es el bot. */
    public boolean vsBot() {
        return seats.stream().anyMatch(MatchSeat::isBot);
    }
}
