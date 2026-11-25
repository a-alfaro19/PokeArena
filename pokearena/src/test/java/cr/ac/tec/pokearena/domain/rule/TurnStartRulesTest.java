package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;

/**
 * Propósito: verificar las reglas puras de preparación y comienzo de turno.
 * Responsabilidades: cubrir mano inicial, robo, límite de mano, energía base y energía extra.
 * Referencia: sección 7.3, paso 5; criterios C.10-E3 a E6.
 */
@DisplayName("Reglas de inicio de turno")
class TurnStartRulesTest {

    private final TurnStartRules rules = new TurnStartRules(3, 7, 2, 1, 6);

    @Test
    @DisplayName("El turno 1 reparte tres cartas y luego roba una carta")
    void firstTurnDealsInitialHandAndDraws() {
        var result = rules.startTurn(TurnStartRules.FIRST_TURN, rules.initialDeck(deckCodes()), List.of(), Map.of());

        assertThat(result.energy()).isEqualTo(2);
        assertThat(result.cards()).filteredOn(card -> card.zone() == MatchZone.HAND)
                .extracting(MatchCard::position).containsExactly(0, 1, 2, 3);
        assertThat(result.cards()).filteredOn(card -> card.zone() == MatchZone.DECK).hasSize(8);
        assertThat(result.cards()).filteredOn(card -> card.zone() == MatchZone.GRAVEYARD).isEmpty();
    }

    @Test
    @DisplayName("Cada turno posterior roba una carta y calcula energía base sin acumular")
    void nextTurnDrawsAndCalculatesTurnEnergy() {
        var initial = rules.startTurn(TurnStartRules.FIRST_TURN, rules.initialDeck(deckCodes()), List.of(), Map.of());
        var next = rules.startTurn(2, initial.cards(), List.of(), Map.of());

        assertThat(next.energy()).isEqualTo(3);
        assertThat(next.cards()).filteredOn(card -> card.zone() == MatchZone.HAND).hasSize(5);
        assertThat(next.cards()).filteredOn(card -> card.zone() == MatchZone.DECK).hasSize(7);
    }

    @Test
    @DisplayName("La energía base por turno es 2, 3, 4, 5, 6 y 7")
    void baseEnergyIncreasesByTurnWithoutAccumulating() {
        List<Integer> energyByTurn = IntStream.rangeClosed(1, 6)
                .mapToObj(turn -> rules.startTurn(turn, rules.initialDeck(deckCodes()), List.of(), Map.of()).energy())
                .toList();

        assertThat(energyByTurn).containsExactly(2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("No roba cuando la mano ya alcanzó el máximo")
    void doesNotDrawAtHandLimit() {
        List<MatchCard> cards = new ArrayList<>(rules.initialDeck(deckCodes()));
        for (int index = 0; index < 7; index++) {
            MatchCard card = cards.get(index);
            cards.set(index, new MatchCard(card.position(), card.cardCode(), MatchZone.HAND));
        }

        var result = rules.startTurn(2, cards, List.of(), Map.of());

        assertThat(result.cards()).filteredOn(card -> card.zone() == MatchZone.HAND).hasSize(7);
        assertThat(result.cards()).filteredOn(card -> card.zone() == MatchZone.DECK).hasSize(5);
    }

    @Test
    @DisplayName("Rechaza un estado con más cartas en mano que el máximo")
    void rejectsHandOverLimit() {
        List<MatchCard> cards = new ArrayList<>(rules.initialDeck(deckCodes()));
        for (int index = 0; index < 8; index++) {
            MatchCard card = cards.get(index);
            cards.set(index, new MatchCard(card.position(), card.cardCode(), MatchZone.HAND));
        }

        assertThatThrownBy(() -> rules.startTurn(2, cards, List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Suma energía fija y energía por carta propia al inicio del turno")
    void appliesConfiguredLocationEnergy() {
        List<AvailableLocation> locations = List.of(
                location(1, new ExtraEnergy(ExtraEnergy.Mode.FIXED, 2)),
                location(2, new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 1)));

        var result = rules.startTurn(2, rules.initialDeck(deckCodes()), locations, Map.of(2L, 3));

        assertThat(result.energy()).isEqualTo(8);
    }

    @Test
    @DisplayName("Rechaza conteos negativos, fuera de la partida o mayores al límite")
    void rejectsInvalidLocationCounts() {
        List<AvailableLocation> locations = List.of(location(2, new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 1)));
        List<MatchCard> cards = rules.initialDeck(deckCodes());

        assertThatThrownBy(() -> rules.startTurn(2, cards, locations, Map.of(2L, -1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.startTurn(2, cards, locations, Map.of(3L, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.startTurn(2, cards, locations, Map.of(2L, 4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza decks incompletos o repetidos y turnos fuera de rango")
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> rules.initialDeck(List.of("PKA-FUE-001")))
                .isInstanceOf(IllegalArgumentException.class);
        List<String> duplicates = new ArrayList<>(deckCodes());
        duplicates.set(1, duplicates.getFirst());
        assertThatThrownBy(() -> rules.initialDeck(duplicates))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.startTurn(7, rules.initialDeck(deckCodes()), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> deckCodes() {
        return IntStream.range(1, DeckRules.DECK_SIZE + 1)
                .mapToObj(index -> "PKA-FUE-%03d".formatted(index))
                .toList();
    }

    private static AvailableLocation location(long id, ExtraEnergy extraEnergy) {
        LocationFields fields = new LocationFields("Location " + id, "Description " + id, 3, extraEnergy);
        return new AvailableLocation(id, Location.create(fields, null, "large", "small", Instant.EPOCH));
    }
}
