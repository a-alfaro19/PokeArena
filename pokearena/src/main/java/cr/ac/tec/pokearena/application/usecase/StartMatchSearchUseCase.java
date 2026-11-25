package cr.ac.tec.pokearena.application.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;
import cr.ac.tec.pokearena.application.dto.StartMatchSearchCommand;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.repository.ActiveMatchLookup;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.BotOfferRules;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.MatchSearchRules;

/** Orquesta el alta clasificatoria y la recuperación de reintentos según A.9. */
public class StartMatchSearchUseCase {
    private static final int EXISTING_SEARCH_STATUS = 200;
    private static final MatchContext CONTEXT = MatchContext.RANKED;

    private final IdempotencyGuard keys;
    private final DeckRepository decks;
    private final PlayerCardRepository cards;
    private final MatchSearchRepository searches;
    private final ActiveMatchLookup matches;
    private final PlayerRepository players;
    private final MatchSearchRules rules;
    private final BotOfferRules botOffer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final long pollSeconds;

    public StartMatchSearchUseCase(IdempotencyGuard keys, DeckRepository decks, PlayerCardRepository cards,
            MatchSearchRepository searches, ActiveMatchLookup matches, PlayerRepository players, MatchSearchRules rules,
            BotOfferRules botOffer, UnitOfWork unitOfWork, Clock clock, Duration pollInterval) {
        this.keys = keys;
        this.decks = decks;
        this.cards = cards;
        this.searches = searches;
        this.matches = matches;
        this.players = players;
        this.rules = rules;
        this.botOffer = botOffer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.pollSeconds = MatchSearchDetails.pollSeconds(pollInterval);
    }

    public IdempotentResult<MatchSearchDetails> execute(IdempotentRequest request, StartMatchSearchCommand command) {
        // A.9: una respuesta ya confirmada se repite aunque después cambie el estado del jugador.
        var previous = keys.findPrevious(request, MatchSearchDetails.class);
        if (previous.isPresent()) {
            return previous.get();
        }

        // A.9: la sesión determina el titular y el deck se vuelve a validar con la colección actual.
        UUID playerId = request.key().userId();
        long deckId = validate(command);
        var deck = decks.findByIdAndPlayer(deckId, playerId)
                .orElseThrow(() -> DeckException.notFound("El deck no existe o no te pertenece."));
        var codes = deck.cardCodes();
        var owned = cards.findOwnedCards(playerId, codes).stream()
                .map(card -> card.stored().card().code().value()).toList();
        DeckRules.validateForPlay(codes, owned).ifPresent(reason -> {
            throw MatchSearchException.invalidDeck(reason);
        });

        // A.9: atender una espera vigente antes de evaluar la partida activa o el saldo.
        var open = searches.findOpenByPlayer(playerId);
        if (open.isPresent()) {
            // Otro intento con esta clave pudo confirmar entre ambas lecturas.
            return keys.findPrevious(request, MatchSearchDetails.class)
                    .orElseGet(() -> existing(open.get(), deckId));
        }
        matches.findActiveMatchId(playerId).ifPresent(matchId -> {
            throw MatchSearchException.activeMatchExists(matchId);
        });
        var player = players.findMatchmakingPlayer(playerId)
                .orElseThrow(() -> new IllegalStateException("No se encontraron los datos del jugador."));
        rules.checkCubes(player.cubeBalance());
        Instant now = clock.now();

        // A.9: confirmar la búsqueda y su respuesta juntas. Resolver conflictos después del rollback.
        try {
            return unitOfWork.execute(() -> {
                var waiting = searches.insertWaiting(playerId, deckId, CONTEXT, player.elo(), now)
                        .orElseThrow(OpenSearchConflict::new);
                var details = MatchSearchDetails.from(waiting, false, pollSeconds);
                keys.record(request, details);
                return IdempotentResult.executed(request, details);
            });
        } catch (IdempotencyConflictException conflict) {
            return keys.resolveConflict(request, MatchSearchDetails.class);
        } catch (OpenSearchConflict conflict) {
            return keys.findPrevious(request, MatchSearchDetails.class).orElseGet(() ->
                    searches.findOpenByPlayer(playerId).map(search -> existing(search, deckId))
                            .orElseThrow(() -> new IllegalStateException(
                                    "La búsqueda que causó el conflicto ya no está disponible.", conflict)));
        }
    }

    private static long validate(StartMatchSearchCommand command) {
        // Se conserva el orden de A.9: el deck omitido tiene prioridad sobre el contexto inválido.
        if (command.deckId() == null) {
            throw ValidationException.missingField("deckId", "Debes seleccionar un deck.");
        }
        if (command.deckId() < 1) {
            throw ValidationException.invalidField("deckId", "El identificador del deck debe ser positivo.", null);
        }
        if (!CONTEXT.name().equals(command.context())) {
            throw ValidationException.invalidField("context", "Selecciona el contexto clasificatorio.",
                    List.of(CONTEXT.name()));
        }
        return command.deckId();
    }

    private IdempotentResult<MatchSearchDetails> existing(MatchSearch search, long deckId) {
        // Reutilizar una espera equivalente preserva el deck y la hora originales.
        if (search.deckId() != deckId || search.context() != CONTEXT) {
            throw MatchSearchException.alreadyOpen(search.id(), search.deckId());
        }
        var details = MatchSearchDetails.from(search, botOffer.isAvailable(search, clock.now()), pollSeconds);
        return new IdempotentResult<>(EXISTING_SEARCH_STATUS, details, false);
    }

    /** Fuerza el rollback antes de consultar la búsqueda o la clave de la solicitud ganadora. */
    private static final class OpenSearchConflict extends RuntimeException {
    }
}
