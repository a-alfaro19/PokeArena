package cr.ac.tec.pokearena.application.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;

/** Vista de la búsqueda que la aplicación entrega al jugador (A.11, mensaje 12). */
public record MatchSearchDetails(UUID id, MatchSearchStatus status, long deckId, MatchContext context,
        Instant createdAt, UUID matchId, boolean botOfferAvailable, String notice, long pollIntervalSeconds) {

    public static MatchSearchDetails from(MatchSearch search, boolean botOfferAvailable, long pollIntervalSeconds) {
        return new MatchSearchDetails(search.id(), search.status(), search.deckId(), search.context(),
                search.createdAt(), search.matchId(), botOfferAvailable, search.notice(), pollIntervalSeconds);
    }

    public static long pollSeconds(Duration pollInterval) {
        if (pollInterval.getSeconds() < 1 || pollInterval.getNano() != 0) {
            throw new IllegalArgumentException("El intervalo de consulta debe ser un número entero positivo de segundos.");
        }
        return pollInterval.getSeconds();
    }
}
