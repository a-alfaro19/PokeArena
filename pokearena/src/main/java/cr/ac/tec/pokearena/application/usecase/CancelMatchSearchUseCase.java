package cr.ac.tec.pokearena.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;

/** Decide la salida según el estado de la fila bloqueada (US 47, A.12). */
public final class CancelMatchSearchUseCase {

    private final MatchSearchRepository searches;
    private final Clock clock;
    private final UnitOfWork unitOfWork;
    private final long pollIntervalSeconds;

    public CancelMatchSearchUseCase(MatchSearchRepository searches, Clock clock, UnitOfWork unitOfWork,
            Duration pollInterval) {
        this.searches = searches;
        this.clock = clock;
        this.unitOfWork = unitOfWork;
        this.pollIntervalSeconds = MatchSearchDetails.pollSeconds(pollInterval);
    }

    public MatchSearchDetails execute(CancelMatchSearchCommand command) {
        UUID id = MatchSearchIds.parse(command.searchId());
        // A.12, mensajes 6 a 9: una sola transacción desde la lectura bajo bloqueo hasta la salida.
        Instant now = clock.now();
        return unitOfWork.execute(() -> {
            MatchSearch locked = searches.lockById(id, command.playerId()).orElseThrow(MatchSearchException::notFound);
            MatchSearch closed = switch (locked.status()) {
                case WAITING -> searches.markCancelled(id, now); // A.12, mensajes 13 a 15.
                case MATCHED -> throw MatchSearchException.alreadyMatched(locked.matchId()); // Mensajes 20 y 21.
                case CANCELLED, EXPIRED -> locked; // Mensajes 24 y 25.
            };
            return MatchSearchDetails.from(closed, false, pollIntervalSeconds);
        });
    }
}
