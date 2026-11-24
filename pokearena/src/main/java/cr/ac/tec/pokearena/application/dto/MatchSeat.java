package cr.ac.tec.pokearena.application.dto;

import java.util.List;
import java.util.UUID;

/** Asiento para preparar una partida. El bot carece de playerId y deckId (sección 7.3). */
public record MatchSeat(UUID playerId, Long deckId, List<String> deckCodes) {
    public MatchSeat {
        deckCodes = List.copyOf(deckCodes);
    }
}
