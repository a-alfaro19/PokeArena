package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: verificar el barajado determinista que usa la preparación de una partida.
 * Responsabilidades: comprobar que una semilla fija reproduce el orden y que el barajado conserva todos los
 * elementos, sin usar {@code java.util.Random}.
 * Referencia: sección 7.3, paso 4; criterio C.10-E2 y C.10-A1.
 */
@DisplayName("Barajado determinista de decks y ubicaciones")
class SeededShuffleTest {

    @Test
    @DisplayName("C.10-A1: la misma semilla reproduce exactamente el orden barajado")
    void sameSeedReproducesOrder() {
        List<String> deck = List.of("A", "B", "C", "D", "E", "F", "G", "H");

        assertThat(SeededShuffle.shuffle(deck, 123456L))
                .isEqualTo(SeededShuffle.shuffle(deck, 123456L));
    }

    @Test
    @DisplayName("C.10-E2: barajar conserva cada carta exactamente una vez")
    void shuffleIsPermutation() {
        List<String> deck = List.of("A", "B", "C", "D", "E", "F", "G", "H");

        assertThat(SeededShuffle.shuffle(deck, 987654L))
                .containsExactlyInAnyOrderElementsOf(deck)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Una lista vacía o de un elemento permanece válida")
    void smallListsAreHandled() {
        assertThat(SeededShuffle.shuffle(List.of(), 1L)).isEmpty();
        assertThat(SeededShuffle.shuffle(List.of("A"), 1L)).containsExactly("A");
    }
}
