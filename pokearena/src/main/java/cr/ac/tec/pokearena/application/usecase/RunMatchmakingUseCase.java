package cr.ac.tec.pokearena.application.usecase;

import java.time.Instant;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.dto.MatchmakingSettings;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.rule.EloWindowRules;

/** Ejecuta un ciclo del emparejador y conserva una transacción por pareja (A.10). */
public class RunMatchmakingUseCase {
    private final MatchSearchRepository searches;
    private final DeckRepository decks;
    private final StartMatch start;
    private final UnitOfWork transactions;
    private final Clock clock;
    private final MatchmakingSettings settings;
    private final EloWindowRules elo;

    public RunMatchmakingUseCase(MatchSearchRepository searches, DeckRepository decks, StartMatch start,
            UnitOfWork transactions, Clock clock, MatchmakingSettings settings) {
        this.searches = searches;
        this.decks = decks;
        this.start = start;
        this.transactions = transactions;
        this.clock = clock;
        this.settings = settings;
        this.elo = new EloWindowRules(settings.initialEloRange(), settings.eloRangeStep(),
                settings.eloRangeStepInterval(), settings.maxEloRange());
    }

    public void execute() {
        // A.10: cerrar las esperas vencidas antes de competir por los bloqueos de una pareja.
        Instant sweptAt = clock.now();
        searches.expireStale(sweptAt.minus(settings.inactivityTimeout()), sweptAt);

        // A.10: avanzar por antigüedad dentro de este ciclo para que una espera sin rival no bloquee a otras.
        MatchSearch cursor = null;
        for (int attempt = 0; attempt < settings.maxPairsPerTick(); attempt++) {
            MatchSearch after = cursor;
            Instant now = clock.now();
            try {
                cursor = transactions.execute(() -> tryPair(now, after));
                if (cursor == null) {
                    return;
                }
            } catch (PairWithoutLocations failure) {
                // La preparación ya se revirtió. Un cierre concurrente tiene prioridad sobre el aviso.
                Instant retryAt = clock.now().plus(settings.failureRetryDelay());
                transactions.execute(() -> searches.markNotice(failure.first.id(), failure.second.id(), retryAt));
                cursor = failure.first;
            }
        }
    }

    private MatchSearch tryPair(Instant now, MatchSearch after) {
        // A.10: la pareja conserva los bloqueos hasta que el mismo UnitOfWork confirme la partida.
        Instant cutoff = now.minus(settings.inactivityTimeout());
        var oldest = searches.lockOldestWaiting(now, cutoff, after);
        if (oldest.isEmpty()) {
            return null;
        }
        MatchSearch first = oldest.get();
        var candidates = searches.lockCandidates(first, now, cutoff, settings.maxEloRange(),
                settings.candidateBatchSize());
        elo.pickCompatible(first, candidates, now).ifPresent(second -> startPair(first, second, now));
        return first;
    }

    private void startPair(MatchSearch first, MatchSearch second, Instant now) {
        // A.10: usar los decks seleccionados al entrar y confirmar ambas asignaciones con la partida.
        var firstSeat = new MatchSeat(first.playerId(), first.deckId(), decks.findCardCodes(first.deckId()));
        var secondSeat = new MatchSeat(second.playerId(), second.deckId(), decks.findCardCodes(second.deckId()));
        var request = new MatchRequest(first.context(), firstSeat, secondSeat);
        try {
            var started = start.execute(request);
            searches.markMatched(first.id(), started.matchId(), now);
            searches.markMatched(second.id(), started.matchId(), now);
        } catch (LocationsUnavailableException failure) {
            throw new PairWithoutLocations(first, second, failure);
        }
    }

    /** Conserva la pareja para avisarla después del rollback. */
    private static final class PairWithoutLocations extends RuntimeException {
        private final MatchSearch first;
        private final MatchSearch second;

        private PairWithoutLocations(MatchSearch first, MatchSearch second, LocationsUnavailableException cause) {
            super(cause);
            this.first = first;
            this.second = second;
        }
    }
}
