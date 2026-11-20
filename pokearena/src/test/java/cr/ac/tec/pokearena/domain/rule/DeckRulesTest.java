package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.model.NewDeck;

/**
 * Propósito: pruebas unitarias de {@link DeckRules} (historia 3.1.3 "Crear Deck").
 * Responsabilidades: comprobar cada regla con su error tipado y sus datos (cantidad actual o códigos), el
 * orden de las comprobaciones de la sección 7.1 (paso 3) y que {@code validateForPlay} informa el motivo de
 * cada regla sin lanzar. Java puro, sin Spring.
 * Referencia: sección 7.1; Anexo A.7, mensajes 18 y 21; criterios C.7-E1, R1, R2 y R3.
 *
 * <p>Los tamaños se calculan a partir de {@link DeckRules#DECK_SIZE} y {@link DeckRules#NAME_MAX_LENGTH}:
 * la prueba comprueba la regla, no repite los números.
 */
@DisplayName("Reglas de un deck (DeckRules)")
class DeckRulesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final String NAME = "Fuego rápido";

    /** {@code count} códigos distintos y válidos: PKA-FUE-001, PKA-FUE-002, ... */
    private static List<String> codes(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> "PKA-FUE-%03d".formatted(n)).toList();
    }

    private static List<String> validCodes() {
        return codes(DeckRules.DECK_SIZE);
    }

    /** Un deck del tamaño exacto en el que la carta de la posición {@code index} repite a la primera. */
    private static List<String> withDuplicateAt(int index) {
        List<String> cards = new ArrayList<>(validCodes());
        cards.set(index, cards.getFirst());
        return cards;
    }

    private static NewDeck shape(String name, List<String> cards) {
        return DeckRules.validateShape(PLAYER, name, cards, NOW);
    }

    private static void assertError(Runnable action, ErrorType type, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, error -> {
            assertThat(error.type()).isEqualTo(type);
            if (field == null) {
                assertThat(error.field()).isEmpty();
            } else {
                assertThat(error.field()).contains(field);
            }
        });
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.7-E1: con un nombre y exactamente 12 cartas distintas, el deck queda listo para guardar")
        void validDeck() {
            NewDeck deck = shape(NAME, validCodes());

            assertThat(deck.playerId()).isEqualTo(PLAYER);
            assertThat(deck.name()).isEqualTo(NAME);
            assertThat(deck.cardCodes()).hasSize(DeckRules.DECK_SIZE).containsExactlyElementsOf(validCodes());
            assertThat(deck.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("El nombre se guarda sin espacios al inicio ni al final, y su forma normalizada sin tildes ni mayúsculas")
        void nameIsStrippedAndNormalized() {
            NewDeck deck = shape("   Fuego RÁPIDO  ", validCodes());

            assertThat(deck.name()).isEqualTo("Fuego RÁPIDO");
            assertThat(deck.nameNormalized()).isEqualTo("fuego rapido");
        }

        @Test
        @DisplayName("Un nombre de exactamente 40 caracteres se acepta, aunque lleve espacios alrededor")
        void nameAtMaximumLength() {
            String name = "a".repeat(DeckRules.NAME_MAX_LENGTH);

            assertThat(shape("  " + name + "  ", validCodes()).name()).isEqualTo(name);
        }

        @Test
        @DisplayName("Un nombre de 1 carácter se acepta")
        void nameAtMinimumLength() {
            assertThat(shape("x", validCodes()).name()).isEqualTo("x");
        }

        @Test
        @DisplayName("El largo del nombre se cuenta en caracteres: 40 emojis se aceptan")
        void lengthCountsCharacters() {
            String name = "🔥".repeat(DeckRules.NAME_MAX_LENGTH);

            assertThat(shape(name, validCodes()).name()).isEqualTo(name);
        }

        @Test
        @DisplayName("El orden en que se envían las cartas no cambia el deck: los códigos quedan ordenados")
        void orderDoesNotMatter() {
            List<String> shuffled = new ArrayList<>(validCodes());
            Collections.reverse(shuffled);

            assertThat(shape(NAME, shuffled).cardCodes()).isEqualTo(shape(NAME, validCodes()).cardCodes());
        }

        @Test
        @DisplayName("Si el jugador posee todas las cartas, checkOwnership no lanza nada")
        void allOwned() {
            DeckRules.checkOwnership(validCodes(), codes(DeckRules.DECK_SIZE + 3));
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @ParameterizedTest(name = "nombre \"{0}\"")
        @ValueSource(strings = { "", "   " })
        @DisplayName("Un nombre vacío o solo con espacios es MISSING_FIELD en name")
        void blankName(String name) {
            assertError(() -> shape(name, validCodes()), ErrorType.MISSING_FIELD, DeckException.NAME_FIELD);
        }

        @Test
        @DisplayName("Sin nombre es MISSING_FIELD en name")
        void missingName() {
            assertError(() -> shape(null, validCodes()), ErrorType.MISSING_FIELD, DeckException.NAME_FIELD);
        }

        @Test
        @DisplayName("Un nombre de 41 caracteres es INVALID_FIELD en name con allowedValues [\"1..40\"]")
        void nameTooLong() {
            String name = "a".repeat(DeckRules.NAME_MAX_LENGTH + 1);

            assertThatThrownBy(() -> shape(name, validCodes())).isInstanceOfSatisfying(DomainException.class, error -> {
                assertThat(error.type()).isEqualTo(ErrorType.INVALID_FIELD);
                assertThat(error.field()).contains(DeckException.NAME_FIELD);
                assertThat(error.allowedValues())
                        .containsExactly(DeckRules.NAME_MIN_LENGTH + ".." + DeckRules.NAME_MAX_LENGTH);
                assertThat(error.getMessage()).contains(String.valueOf(DeckRules.NAME_MAX_LENGTH + 1));
            });
        }

        @Test
        @DisplayName("Sin lista de cartas es MISSING_FIELD en cards")
        void missingCards() {
            assertError(() -> shape(NAME, null), ErrorType.MISSING_FIELD, DeckException.CARDS_FIELD);
        }

        @ParameterizedTest(name = "{0} cartas")
        @ValueSource(ints = { DeckRules.DECK_SIZE - 1, DeckRules.DECK_SIZE + 1, 0 })
        @DisplayName("C.7-R1: un deck con menos o más de 12 cartas es INVALID_DECK_SIZE e indica la cantidad actual")
        void wrongSize(int count) {
            assertThatThrownBy(() -> shape(NAME, codes(count))).isInstanceOfSatisfying(DeckException.class, error -> {
                assertThat(error.type()).isEqualTo(ErrorType.INVALID_DECK_SIZE);
                assertThat(error.field()).contains(DeckException.CARDS_FIELD);
                assertThat(error.getMessage())
                        .isEqualTo("El deck debe tener exactamente " + DeckRules.DECK_SIZE + " cartas y tiene "
                                + count + ".");
            });
        }

        @Test
        @DisplayName("C.7-R2: un deck con cartas repetidas es DUPLICATE_DECK_CARDS e indica cuáles, ordenadas y sin repetir")
        void duplicates() {
            List<String> cards = new ArrayList<>(validCodes());
            // PKA-FUE-002 aparece tres veces y PKA-FUE-001 dos: el mensaje los lista una vez cada uno, en orden.
            cards.set(5, "PKA-FUE-002");
            cards.set(6, "PKA-FUE-002");
            cards.set(7, "PKA-FUE-001");

            assertThatThrownBy(() -> shape(NAME, cards)).isInstanceOfSatisfying(DeckException.class, error -> {
                assertThat(error.type()).isEqualTo(ErrorType.DUPLICATE_DECK_CARDS);
                assertThat(error.field()).contains(DeckException.CARDS_FIELD);
                assertThat(error.getMessage()).isEqualTo("El deck tiene cartas repetidas: PKA-FUE-001, PKA-FUE-002.");
            });
        }

        @Test
        @DisplayName("Un elemento nulo o vacío en la lista es INVALID_FIELD en cards")
        void blankCode() {
            List<String> withNull = new ArrayList<>(validCodes());
            withNull.set(0, null);
            List<String> withBlank = new ArrayList<>(validCodes());
            withBlank.set(0, " ");

            assertError(() -> shape(NAME, withNull), ErrorType.INVALID_FIELD, DeckException.CARDS_FIELD);
            assertError(() -> shape(NAME, withBlank), ErrorType.INVALID_FIELD, DeckException.CARDS_FIELD);
        }

        @Test
        @DisplayName("C.7-R3: un deck con cartas que el jugador no posee es CARD_NOT_OWNED e indica cuáles, ordenadas")
        void notOwned() {
            List<String> owned = validCodes().subList(2, DeckRules.DECK_SIZE);

            assertThatThrownBy(() -> DeckRules.checkOwnership(validCodes(), owned))
                    .isInstanceOfSatisfying(DeckException.class, error -> {
                        assertThat(error.type()).isEqualTo(ErrorType.CARD_NOT_OWNED);
                        assertThat(error.field()).contains(DeckException.CARDS_FIELD);
                        assertThat(error.getMessage()).isEqualTo("No posees estas cartas: PKA-FUE-001, PKA-FUE-002.");
                    });
        }

        @Test
        @DisplayName("C.7-R3: si el jugador no posee ninguna carta, el mensaje las lista todas")
        void noneOwned() {
            assertThatThrownBy(() -> DeckRules.checkOwnership(validCodes(), List.of()))
                    .isInstanceOfSatisfying(DeckException.class,
                            error -> assertThat(error.getMessage()).contains(validCodes().toArray(String[]::new)));
        }
    }

    @Nested
    @DisplayName("Orden de las comprobaciones (sección 7.1, paso 3)")
    class Order {

        // Cada caso rompe varias reglas a la vez; debe aparecer solo el error de la primera.

        @Test
        @DisplayName("El nombre se comprueba antes que la lista: sin nombre y sin cartas es MISSING_FIELD en name")
        void nameBeforeCards() {
            assertError(() -> shape(" ", null), ErrorType.MISSING_FIELD, DeckException.NAME_FIELD);
        }

        @Test
        @DisplayName("El largo del nombre se comprueba antes que la cantidad: nombre de 41 y 11 cartas es INVALID_FIELD")
        void nameLengthBeforeSize() {
            String name = "a".repeat(DeckRules.NAME_MAX_LENGTH + 1);

            assertError(() -> shape(name, codes(DeckRules.DECK_SIZE - 1)), ErrorType.INVALID_FIELD,
                    DeckException.NAME_FIELD);
        }

        @Test
        @DisplayName("La lista presente se comprueba antes que la cantidad: sin cartas es MISSING_FIELD, no INVALID_DECK_SIZE")
        void cardsPresentBeforeSize() {
            assertError(() -> shape(NAME, null), ErrorType.MISSING_FIELD, DeckException.CARDS_FIELD);
        }

        @Test
        @DisplayName("La cantidad se comprueba antes que las repetidas: 13 cartas con repetidas es INVALID_DECK_SIZE")
        void sizeBeforeDuplicates() {
            List<String> cards = new ArrayList<>(withDuplicateAt(1));
            cards.add("PKA-AGU-001");

            assertError(() -> shape(NAME, cards), ErrorType.INVALID_DECK_SIZE, DeckException.CARDS_FIELD);
        }

        @Test
        @DisplayName("La cantidad se comprueba antes que los códigos vacíos: 13 cartas con un nulo es INVALID_DECK_SIZE")
        void sizeBeforeBlankCodes() {
            List<String> cards = new ArrayList<>(validCodes());
            cards.add(null);

            assertError(() -> shape(NAME, cards), ErrorType.INVALID_DECK_SIZE, DeckException.CARDS_FIELD);
        }

        @Test
        @DisplayName("Los códigos vacíos se comprueban antes que las repetidas: 12 cartas con un nulo y repetidas es INVALID_FIELD")
        void blankCodesBeforeDuplicates() {
            List<String> cards = new ArrayList<>(withDuplicateAt(1));
            cards.set(2, null);

            assertError(() -> shape(NAME, cards), ErrorType.INVALID_FIELD, DeckException.CARDS_FIELD);
        }

        @Test
        @DisplayName("Con nombre y lista válidos y 12 cartas, recién entonces aparecen las repetidas")
        void duplicatesWhenEverythingElsePasses() {
            assertError(() -> shape(NAME, withDuplicateAt(1)), ErrorType.DUPLICATE_DECK_CARDS,
                    DeckException.CARDS_FIELD);
        }
    }

    @Nested
    @DisplayName("Jugable al buscar partida (validateForPlay, sección 7.2.1)")
    class ForPlay {

        @Test
        @DisplayName("Un deck de 12 cartas distintas y todas poseídas es jugable: sin motivo")
        void playable() {
            assertThat(DeckRules.validateForPlay(validCodes(), validCodes())).isEmpty();
        }

        @Test
        @DisplayName("Con otra cantidad de cartas, el motivo indica la cantidad actual, sin lanzar")
        void wrongSize() {
            List<String> cards = codes(DeckRules.DECK_SIZE - 1);

            assertThat(DeckRules.validateForPlay(cards, cards)).contains(
                    "El deck debe tener exactamente " + DeckRules.DECK_SIZE + " cartas y tiene "
                            + (DeckRules.DECK_SIZE - 1) + ".");
        }

        @Test
        @DisplayName("Con cartas repetidas, el motivo lista los códigos repetidos")
        void duplicates() {
            List<String> cards = withDuplicateAt(1);

            assertThat(DeckRules.validateForPlay(cards, cards))
                    .contains("El deck tiene cartas repetidas: PKA-FUE-001.");
        }

        @Test
        @DisplayName("Con cartas que ya no posee, el motivo lista los códigos que faltan")
        void notOwned() {
            List<String> owned = validCodes().subList(1, DeckRules.DECK_SIZE);

            assertThat(DeckRules.validateForPlay(validCodes(), owned)).contains("No posees estas cartas: PKA-FUE-001.");
        }

        @Test
        @DisplayName("Mismo orden que la creación: con otra cantidad y cartas no poseídas, informa la cantidad")
        void sameOrderAsCreation() {
            assertThat(DeckRules.validateForPlay(codes(DeckRules.DECK_SIZE + 1), List.of()))
                    .hasValueSatisfying(reason -> assertThat(reason).startsWith("El deck debe tener exactamente"));
        }

        @Test
        @DisplayName("Mismas reglas que la creación: el motivo es el mismo texto que el error de crear el deck")
        void sameMessagesAsCreation() {
            List<String> duplicated = withDuplicateAt(1);
            List<String> owned = validCodes().subList(1, DeckRules.DECK_SIZE);

            assertThat(DeckRules.validateForPlay(duplicated, duplicated)).contains(messageOf(() -> shape(NAME, duplicated)));
            assertThat(DeckRules.validateForPlay(validCodes(), owned))
                    .contains(messageOf(() -> DeckRules.checkOwnership(validCodes(), owned)));
        }

        private static String messageOf(Runnable action) {
            try {
                action.run();
            } catch (DeckException error) {
                return error.getMessage();
            }
            throw new AssertionError("Se esperaba un DeckException");
        }
    }

    @Test
    @DisplayName("Las cartas del deck no se pueden modificar después de validar: la lista es una copia inmutable")
    void codesAreAnImmutableCopy() {
        List<String> cards = new ArrayList<>(validCodes());
        NewDeck deck = shape(NAME, cards);
        cards.set(0, "PKA-AGU-999");

        assertThat(deck.cardCodes()).doesNotContain("PKA-AGU-999");
        assertThatThrownBy(() -> deck.cardCodes().add("otra")).isInstanceOf(UnsupportedOperationException.class);
    }
}
