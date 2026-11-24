package cr.ac.tec.pokearena.api.dto;

import java.time.Instant;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;

/** Cuerpo HTTP de la consulta de búsqueda (US 47, sección 7.2.3). */
public record MatchSearchResponse(UUID id, String status, long deckId, String context, Instant createdAt,
        UUID matchId, boolean botOfferAvailable, String notice, long pollIntervalSeconds) {

    public static MatchSearchResponse from(MatchSearchDetails search) {
        return new MatchSearchResponse(search.id(), search.status().name(), search.deckId(), search.context().name(),
                search.createdAt(), search.matchId(), search.botOfferAvailable(), search.notice(),
                search.pollIntervalSeconds());
    }
}
