package cr.ac.tec.pokearena.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.AcceptBotMatchCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.rule.BotOfferRules;

/** Aceptación idempotente del bot, US 47, A.13. La preparación comparte la transacción de la búsqueda. */
public final class AcceptBotMatchUseCase {
    private final MatchSearchRepository searches;
    private final DeckRepository decks;
    private final BotOfferRules offers;
    private final BotDeckSelector bots;
    private final StartMatch start;
    private final IdempotencyGuard keys;
    private final UnitOfWork uow;
    private final Clock clock;
    private final Duration inactivityTimeout;
    private final long pollSeconds;

    public AcceptBotMatchUseCase(MatchSearchRepository searches, DeckRepository decks, BotOfferRules offers,
            BotDeckSelector bots, StartMatch start, IdempotencyGuard keys, UnitOfWork uow, Clock clock,
            Duration inactivityTimeout, Duration pollInterval) {
        this.searches = searches;
        this.decks = decks;
        this.offers = offers;
        this.bots = bots;
        this.start = start;
        this.keys = keys;
        this.uow = uow;
        this.clock = clock;
        this.inactivityTimeout = inactivityTimeout;
        this.pollSeconds = MatchSearchDetails.pollSeconds(pollInterval);
    }

    public IdempotentResult<MatchSearchDetails> execute(IdempotentRequest request, AcceptBotMatchCommand command) {
        // A.13, mensajes 6 a 9: devolver el éxito anterior antes de interpretar el estado actual.
        var previous = keys.findPrevious(request, MatchSearchDetails.class);
        if (previous.isPresent()) {
            return previous.get();
        }
        UUID id = MatchSearchIds.parse(command.searchId());
        Instant now = clock.now();
        try {
            var details = uow.execute(() -> accept(request, id, now));
            return IdempotentResult.executed(request, details);
        } catch (LocationsUnavailableException unavailable) {
            throw MatchSearchException.preparationFailed();
        } catch (IdempotencyConflictException conflict) {
            return keys.resolveConflict(request, MatchSearchDetails.class);
        } catch (MatchSearchException failure) {
            if (failure.type() != ErrorType.SEARCH_ALREADY_MATCHED) {
                throw failure;
            }
            // Si el otro POST ganó el bloqueo, la clave ya es visible después del rollback (anexo E).
            return keys.findPrevious(request, MatchSearchDetails.class).orElseThrow(() -> failure);
        }
    }

    private MatchSearchDetails accept(IdempotentRequest request, UUID id, Instant now) {
        // A.13, mensajes 13 a 16: comprobar titularidad, estado y oferta con la fila bloqueada.
        MatchSearch search = searches.lockById(id, request.key().userId()).orElseThrow(MatchSearchException::notFound);
        if (search.status() == MatchSearchStatus.MATCHED) {
            throw MatchSearchException.alreadyMatched(search.matchId());
        }
        if (search.status() != MatchSearchStatus.WAITING || search.lastPolledAt().isBefore(now.minus(inactivityTimeout))) {
            throw MatchSearchException.notOpen();
        }
        offers.checkAvailable(search, now);
        // A.13, mensajes 17 a 22: el jugador conserva el deck indicado al entrar.
        MatchSeat bot = new MatchSeat(null, null, bots.select());
        MatchSeat human = new MatchSeat(search.playerId(), search.deckId(), decks.findCardCodes(search.deckId()));
        var started = start.execute(new MatchRequest(search.context(), human, bot));
        var matched = searches.markMatched(id, started.matchId(), now);
        var details = MatchSearchDetails.from(matched, false, pollSeconds);
        // A.13, mensajes 23 a 25: el éxito y la partida se confirman juntos.
        keys.record(request, details);
        return details;
    }
}
