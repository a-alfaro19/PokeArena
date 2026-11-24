package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.model.MatchCard;
import cr.ac.tec.pokearena.domain.model.MatchZone;
import cr.ac.tec.pokearena.domain.model.TurnStartResult;

/**
 * Propósito: probar las reglas de reparto, robo y energía al inicio de cada turno.
 * Responsabilidades: fijar el tamaño de la mano inicial, el robo con tope, la energía no acumulable y los dos
 * modos de energía extra por ubicación.
 * Referencia: sección 7.3 ("Inicio de turno"); criterios C.10-E2, E3, E4, E5 y E8.
 */
@DisplayName("Reglas de inicio de turno")
class TurnStartRulesTest {

    private static final int INITIAL_HAND_SIZE = 3;
    private static final int MAX_HAND_SIZE = 7;
    private static final int INITIAL_ENERGY = 2;
    private static final int ENERGY_PER_TURN = 1;
    private static final int MAX_TURNS = 6;
    private static final Instant CREATED_AT = Instant.parse("2026-11-20T12:00:00Z");

    private final TurnStartRules rules = new TurnStartRules(
            INITIAL_HAND_SIZE, MAX_HAND_SIZE, INITIAL_ENERGY, ENERGY_PER_TURN, MAX_TURNS);

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.10-E2: el turno 1 reparte tres cartas iniciales y también roba la carta del turno")
        void firstTurnDealsInitialHandAndDraws() {
            TurnStartResult result = rules.startTurn(1, deck(12), List.of(), Map.of());

            assertThat(inZone(result.cards(), MatchZone.HAND)).extracting(MatchCard::position)
                    .containsExactly(0, 1, 2, 3);
            assertThat(inZone(result.cards(), MatchZone.DECK)).hasSize(8);
            assertThat(inZone(result.cards(), MatchZone.GRAVEYARD)).isEmpty();
        }

        @Test
        @DisplayName("C.10-E3: roba una carta al inicio de turnos posteriores mientras la mano tenga espacio")
        void drawsOncePerTurnWhileHandHasRoom() {
            List<MatchCard> cards = deck(12);
            TurnStartResult first = rules.startTurn(1, cards, List.of(), Map.of());
            TurnStartResult second = rules.startTurn(2, first.cards(), List.of(), Map.of());

            assertThat(inZone(first.cards(), MatchZone.HAND)).hasSize(INITIAL_HAND_SIZE + 1);
            assertThat(inZone(second.cards(), MatchZone.HAND)).hasSize(INITIAL_HAND_SIZE + 2);
            assertThat(inZone(second.cards(), MatchZone.DECK)).hasSize(12 - INITIAL_HAND_SIZE - 2);
        }

        @Test
        @DisplayName("C.10-E3: con siete cartas en la mano no roba")
        void fullHandDoesNotDraw() {
            List<MatchCard> cards = new ArrayList<>();
            for (int position = 0; position < MAX_HAND_SIZE; position++) {
                cards.add(new MatchCard("hand-" + position, position, MatchZone.HAND));
            }
            cards.add(new MatchCard("deck", MAX_HAND_SIZE, MatchZone.DECK));

            TurnStartResult result = rules.startTurn(2, cards, List.of(), Map.of());

            assertThat(inZone(result.cards(), MatchZone.HAND)).hasSize(MAX_HAND_SIZE);
            assertThat(inZone(result.cards(), MatchZone.DECK)).extracting(MatchCard::code).containsExactly("deck");
        }

        @Test
        @DisplayName("C.10-E4: la energía base de los turnos uno a seis es 2, 3, 4, 5, 6 y 7 sin acumular")
        void energyIsRecomputedForEachTurn() {
            List<MatchCard> cards = deck(12);
            List<Integer> energyByTurn = new ArrayList<>();
            for (int turn = 1; turn <= MAX_TURNS; turn++) {
                TurnStartResult result = rules.startTurn(turn, cards, List.of(), Map.of());
                energyByTurn.add(result.energy());
                cards = result.cards();
            }

            assertThat(energyByTurn).containsExactly(2, 3, 4, 5, 6, 7);
        }

        @Test
        @DisplayName("C.10-E5: energía fija y por carta se suman usando el conteo propio al inicio del turno")
        void extraEnergyUsesLocationConfigurationAndCardCounts() {
            AvailableLocation fixed = available(11, ExtraEnergy.Mode.FIXED, 2);
            AvailableLocation perCard = available(12, ExtraEnergy.Mode.PER_CARD, 2);
            AvailableLocation none = available(13, ExtraEnergy.Mode.NONE, 0);

            TurnStartResult result = rules.startTurn(1, deck(12), List.of(fixed, perCard, none),
                    Map.of(fixed.id(), 8, perCard.id(), 3));

            assertThat(result.energy()).isEqualTo(INITIAL_ENERGY + 2 + 2 * 3);
        }

        @Test
        @DisplayName("La energía de turno dos parte de su base y no conserva la energía fija del turno anterior")
        void previousEnergyIsNotCarriedForward() {
            AvailableLocation fixed = available(21, ExtraEnergy.Mode.FIXED, 2);
            TurnStartResult first = rules.startTurn(1, deck(12), List.of(fixed), Map.of());
            TurnStartResult second = rules.startTurn(2, first.cards(), List.of(fixed), Map.of());

            assertThat(first.energy()).isEqualTo(INITIAL_ENERGY + 2);
            assertThat(second.energy()).isEqualTo(INITIAL_ENERGY + ENERGY_PER_TURN + 2);
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("Un número de turno fuera de la partida se rechaza")
        void rejectsTurnOutsideMatch() {
            assertThatThrownBy(() -> rules.startTurn(MAX_TURNS + 1, deck(12), List.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Un conteo negativo de cartas por ubicación se rechaza")
        void rejectsNegativeLocationCount() {
            AvailableLocation perCard = available(31, ExtraEnergy.Mode.PER_CARD, 1);

            assertThatThrownBy(() -> rules.startTurn(1, deck(12), List.of(perCard), Map.of(perCard.id(), -1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static List<MatchCard> deck(int count) {
        return TurnStartRules.initialDeck(
                IntStream.range(0, count).mapToObj(index -> "PKA-FUE-%03d".formatted(index + 1)).toList());
    }

    private static List<MatchCard> inZone(List<MatchCard> cards, MatchZone zone) {
        return cards.stream().filter(card -> card.zone() == zone).toList();
    }

    private static AvailableLocation available(long id, ExtraEnergy.Mode mode, int amount) {
        LocationFields fields = new LocationFields("Lugar " + id, "Descripción " + id, 3,
                new ExtraEnergy(mode, amount));
        Location location = Location.create(fields, null, "/images/locations/" + id + "/large",
                "/images/locations/" + id + "/small", CREATED_AT);
        return new AvailableLocation(id, location);
    }
}
