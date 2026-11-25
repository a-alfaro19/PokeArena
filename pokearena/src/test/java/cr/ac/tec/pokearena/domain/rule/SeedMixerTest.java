package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: verificar la derivación determinista de subsemillas.
 * Responsabilidades: comprobar estabilidad, separación por propósito y validación de entrada.
 * Referencia: sección 7.3; Anexo A.14.
 */
@DisplayName("Derivación de semillas")
class SeedMixerTest {

    @Test
    @DisplayName("La derivación es estable para la misma semilla y propósito")
    void sameInputProducesSameSeed() {
        assertThat(SeedMixer.derive(417L, "seat-1"))
                .isEqualTo(SeedMixer.derive(417L, "seat-1"));
    }

    @Test
    @DisplayName("Cada propósito deriva una semilla separada")
    void purposesProduceSeparateSeeds() {
        assertThat(SeedMixer.derive(417L, "locations"))
                .isNotEqualTo(SeedMixer.derive(417L, "seat-1"));
    }

    @Test
    @DisplayName("El propósito debe existir y no estar vacío")
    void purposeIsRequired() {
        assertThatThrownBy(() -> SeedMixer.derive(417L, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeedMixer.derive(417L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
