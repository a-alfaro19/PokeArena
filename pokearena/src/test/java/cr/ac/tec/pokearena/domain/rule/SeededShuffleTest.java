package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: verificar el barajado determinista del dominio.
 * Responsabilidades: comprobar la reproducibilidad, la permutación y la no mutación de la entrada.
 * Referencia: sección 7.3; decisión D-S3-05.
 */
@DisplayName("Barajado determinista")
class SeededShuffleTest {

    @Test
    @DisplayName("La misma semilla reproduce el orden y no modifica la lista de entrada")
    void sameSeedReproducesPermutationWithoutChangingInput() {
        List<Integer> input = IntStream.range(0, 12).boxed().toList();

        List<Integer> first = SeededShuffle.shuffle(input, 971L);
        List<Integer> second = SeededShuffle.shuffle(input, 971L);

        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first).containsExactlyInAnyOrderElementsOf(input);
        assertThat(input).containsExactlyElementsOf(IntStream.range(0, 12).boxed().toList());
    }

    @Test
    @DisplayName("Barajar una lista vacía o con un elemento conserva su contenido")
    void smallInputsArePreserved() {
        assertThat(SeededShuffle.shuffle(List.of(), 17L)).isEmpty();
        assertThat(SeededShuffle.shuffle(List.of("PKA-FUE-001"), 17L)).containsExactly("PKA-FUE-001");
    }
}
