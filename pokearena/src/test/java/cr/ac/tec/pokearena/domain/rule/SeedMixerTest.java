package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: comprobar que la derivación de semillas es reproducible y queda separada por propósito.
 * Responsabilidades: fijar el contrato determinista que permite repetir una partida y aislar las fuentes
 * de azar de ubicaciones y asientos.
 * Referencia: sección 7.3 ("Semillas"); criterio C.10-A1 y C.10-R2.
 */
@DisplayName("Derivación de semillas de partida")
class SeedMixerTest {

    @Test
    @DisplayName("Con la misma semilla y propósito se obtiene siempre la misma semilla derivada")
    void sameInputProducesSameSeed() {
        assertThat(SeedMixer.derive(4815L, "locations"))
                .isEqualTo(SeedMixer.derive(4815L, "locations"));
    }

    @Test
    @DisplayName("Los propósitos de ubicaciones y asientos producen semillas independientes")
    void differentPurposesProduceDifferentSeeds() {
        assertThat(SeedMixer.derive(4815L, "locations"))
                .isNotEqualTo(SeedMixer.derive(4815L, "seat-1"))
                .isNotEqualTo(SeedMixer.derive(4815L, "seat-2"));
    }

    @Test
    @DisplayName("Un propósito vacío se rechaza para evitar semillas sin significado")
    void blankPurposeIsRejected() {
        assertThatThrownBy(() -> SeedMixer.derive(4815L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
