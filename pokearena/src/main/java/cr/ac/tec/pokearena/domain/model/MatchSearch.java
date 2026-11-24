package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado guardado de una búsqueda (sección 6.3, diagrama A.11).
 * createdAt marca el inicio de la espera y lastPolledAt la actividad del jugador.
 */
public record MatchSearch(UUID id, UUID playerId, long deckId, MatchContext context,
        MatchSearchStatus status, int elo, UUID matchId, String notice, Instant notBefore,
        Instant createdAt, Instant lastPolledAt, Instant closedAt) {
}
