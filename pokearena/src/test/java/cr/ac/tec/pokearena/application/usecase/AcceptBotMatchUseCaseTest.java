package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import cr.ac.tec.pokearena.application.dto.AcceptBotMatchCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchSearchDetails;
import cr.ac.tec.pokearena.application.dto.MatchSeat;
import cr.ac.tec.pokearena.application.dto.MatchStarted;
import cr.ac.tec.pokearena.application.port.IdempotencyKey;
import cr.ac.tec.pokearena.application.port.StartMatch;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.LocationsUnavailableException;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;
import cr.ac.tec.pokearena.domain.model.MatchSearchStatus;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.MatchSearchRepository;
import cr.ac.tec.pokearena.domain.rule.BotOfferRules;
import cr.ac.tec.pokearena.domain.rule.DeckRules;

class AcceptBotMatchUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private final UUID player = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final UUID match = UUID.randomUUID();
    private final IdempotentRequest request = new IdempotentRequest(new IdempotencyKey("abc", player, "acceptBot"),
            id.toString(), 201);
    private final AcceptBotMatchCommand command = new AcceptBotMatchCommand(id.toString());
    private final MatchSearchRepository searches = mock(MatchSearchRepository.class);
    private final DeckRepository decks = mock(DeckRepository.class);
    private final CollectibleCardRepository cards = mock(CollectibleCardRepository.class);
    private final StartMatch start = mock(StartMatch.class);
    private final IdempotencyGuard keys = mock(IdempotencyGuard.class);
    private final UnitOfWork uow = new UnitOfWork() {
        public <T> T execute(Supplier<T> work) { return work.get(); }
    };
    private final AcceptBotMatchUseCase accept = new AcceptBotMatchUseCase(searches, decks,
            new BotOfferRules(Duration.ofSeconds(60)), new BotDeckSelector(cards, () -> 123), start, keys, uow,
            () -> NOW, Duration.ofSeconds(30), Duration.ofSeconds(5));

    private MatchSearch row(MatchSearchStatus status, Instant created, Instant polled) {
        return new MatchSearch(id, player, 7, MatchContext.RANKED, status, 0,
                status == MatchSearchStatus.MATCHED ? match : null, null, null, created, polled,
                status == MatchSearchStatus.WAITING ? null : NOW);
    }

    private void locked(MatchSearch row) {
        when(searches.lockById(id, player)).thenReturn(Optional.of(row));
    }

    private void assertError(ErrorType type) {
        assertThatThrownBy(() -> accept.execute(request, command)).isInstanceOfSatisfying(
                MatchSearchException.class, error -> assertThat(error.type()).isEqualTo(type));
    }

    @Test
    @DisplayName("C.9-E3: prepara ambos decks, asocia la partida y registra el resultado")
    void accepts() {
        locked(row(MatchSearchStatus.WAITING, NOW.minusSeconds(60), NOW));
        List<String> human = codes("jugador");
        List<String> bot = codes("bot");
        when(decks.findCardCodes(7)).thenReturn(human);
        when(cards.pickRandomCollectible("COMMON", 123, DeckRules.DECK_SIZE)).thenReturn(bot);
        when(start.execute(new MatchRequest(MatchContext.RANKED, new MatchSeat(player, 7L, human),
                new MatchSeat(null, null, bot)))).thenReturn(new MatchStarted(match));
        when(searches.markMatched(id, match, NOW)).thenReturn(row(MatchSearchStatus.MATCHED, NOW.minusSeconds(60), NOW));
        var result = accept.execute(request, command);
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().matchId()).isEqualTo(match);
        verify(keys).record(request, result.body());
    }

    @ParameterizedTest
    @EnumSource(value = MatchSearchStatus.class, names = {"CANCELLED", "EXPIRED"})
    @DisplayName("C.9-E3: una búsqueda cerrada rechaza jugar con el bot")
    void rejectsClosed(MatchSearchStatus status) {
        locked(row(status, NOW.minusSeconds(70), NOW));
        assertError(ErrorType.SEARCH_NOT_OPEN);
        verifyNoInteractions(start, cards);
    }

    @Test
    @DisplayName("C.9-R1: una búsqueda inactiva no obtiene partida aunque el job aún no la haya cerrado")
    void rejectsInactive() {
        locked(row(MatchSearchStatus.WAITING, NOW.minusSeconds(90), NOW.minusSeconds(31)));
        assertError(ErrorType.SEARCH_NOT_OPEN);
        verifyNoInteractions(start, cards);
    }

    @Test
    @DisplayName("C.9-E3: antes del umbral no se inicia la partida")
    void rejectsEarlyOffer() {
        locked(row(MatchSearchStatus.WAITING, NOW.minusSeconds(59), NOW));
        assertError(ErrorType.BOT_OFFER_NOT_AVAILABLE);
        verifyNoInteractions(start, cards);
    }

    @Test
    @DisplayName("C.9-E3: una búsqueda inexistente o ajena devuelve SEARCH_NOT_FOUND")
    void rejectsMissing() {
        assertError(ErrorType.SEARCH_NOT_FOUND);
        verifyNoInteractions(start);
    }

    @Test
    @DisplayName("C.9-E3: una partida ya asignada se identifica en el rechazo")
    void rejectsAlreadyMatched() {
        locked(row(MatchSearchStatus.MATCHED, NOW.minusSeconds(70), NOW));
        assertThatThrownBy(() -> accept.execute(request, command)).isInstanceOfSatisfying(MatchSearchException.class,
                error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.SEARCH_ALREADY_MATCHED);
                    assertThat(error.getMessage()).contains(match.toString());
                });
    }

    @Test
    @DisplayName("C.10-R1: si faltan ubicaciones se informa el error de preparación")
    void rejectsFailedPreparation() {
        locked(row(MatchSearchStatus.WAITING, NOW.minusSeconds(60), NOW));
        when(cards.pickRandomCollectible("COMMON", 123, DeckRules.DECK_SIZE)).thenReturn(codes("bot"));
        when(decks.findCardCodes(7)).thenReturn(codes("jugador"));
        when(start.execute(any())).thenThrow(new LocationsUnavailableException());
        assertError(ErrorType.MATCH_PREPARATION_FAILED);
        verify(searches, never()).markMatched(eq(id), any(), any());
    }

    @Test
    @DisplayName("C.9-E3: un reintento posterior al commit recupera la respuesta guardada")
    void replaySkipsPreparation() {
        var saved = savedMatch();
        when(keys.findPrevious(request, MatchSearchDetails.class)).thenReturn(Optional.of(saved));
        assertThat(accept.execute(request, command)).isEqualTo(saved);
        verifyNoInteractions(searches, cards, decks, start);
    }

    @Test
    @DisplayName("C.9-E3: si otro envío ganó el bloqueo se recupera su respuesta tras el rollback")
    void replayAfterConcurrentMatch() {
        var saved = savedMatch();
        when(keys.findPrevious(request, MatchSearchDetails.class)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved));
        locked(row(MatchSearchStatus.MATCHED, NOW.minusSeconds(65), NOW));
        assertThat(accept.execute(request, command)).isEqualTo(saved);
        verifyNoInteractions(start, cards);
    }

    private IdempotentResult<MatchSearchDetails> savedMatch() {
        var details = MatchSearchDetails.from(row(MatchSearchStatus.MATCHED, NOW.minusSeconds(65), NOW), false, 5);
        return new IdempotentResult<>(201, details, true);
    }

    private static List<String> codes(String prefix) {
        return IntStream.range(0, DeckRules.DECK_SIZE).mapToObj(i -> prefix + i).toList();
    }
}
