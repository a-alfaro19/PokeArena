package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.MatchSearchException;
import cr.ac.tec.pokearena.domain.repository.CollectibleCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;

class BotDeckSelectorTest {

    private static final long SEED = 702L;

    @Test
    @DisplayName("C.9-E3: el bot pide un deck completo de Comunes con la semilla del servidor")
    void selectsCommonCards() {
        List<String> codes = IntStream.rangeClosed(1, DeckRules.DECK_SIZE)
                .mapToObj(i -> "PKA-FUE-%03d".formatted(i)).toList();
        assertThat(selectorReturning(codes).select()).containsExactlyElementsOf(codes);
    }

    @Test
    @DisplayName("C.9-E3: un catálogo insuficiente impide preparar una partida con un deck incompleto")
    void rejectsIncompleteBotDeck() {
        assertThatThrownBy(() -> selectorReturning(List.of("PKA-FUE-001")).select())
                .isInstanceOfSatisfying(MatchSearchException.class,
                        error -> assertThat(error.type()).isEqualTo(ErrorType.MATCH_PREPARATION_FAILED));
    }

    private BotDeckSelector selectorReturning(List<String> codes) {
        CollectibleCardRepository cards = new CollectibleCardRepository() {
            @Override
            public List<String> pickRandomCollectible(String rarity, long seed, int count) {
                assertThat(rarity).isEqualTo("COMMON");
                assertThat(seed).isEqualTo(SEED);
                assertThat(count).isEqualTo(DeckRules.DECK_SIZE);
                return codes;
            }

            @Override
            public int countCollectible(String rarity) {
                throw new AssertionError("La selección ya devuelve las cartas disponibles.");
            }
        };
        return new BotDeckSelector(cards, () -> SEED);
    }
}
