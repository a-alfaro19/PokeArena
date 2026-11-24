package cr.ac.tec.pokearena.application.dto;

import java.time.Instant;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;

/** Vista de la búsqueda que la aplicación entrega al jugador (A.11, mensaje 12). */
public record MatchSearchDetails(UUID id, MatchSearchStatus status, long deckId, MatchContext context,
        Instant createdAt, UUID matchId, boolean botOfferAvailable, String notice, long pollIntervalSeconds) {
}
