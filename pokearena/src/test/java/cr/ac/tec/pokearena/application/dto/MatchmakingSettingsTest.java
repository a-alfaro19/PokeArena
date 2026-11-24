package cr.ac.tec.pokearena.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: comprobar las invariantes de configuración del emparejamiento.
 * Responsabilidades: rechazar rangos Elo incompatibles, intervalos inválidos y límites de procesamiento no
 * positivos antes del arranque del sistema.
 * Referencia: sección 5.7.
 */
@DisplayName("Configuración del emparejamiento")
class MatchmakingSettingsTest {

    @Test
    @DisplayName("El rango máximo no puede ser menor que el rango inicial")
    void maxRangeCannotBeBelowInitialRange() {
        assertThatThrownBy(() -> settings(100, 50, 80, 20, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Los intervalos y tamaños de lote deben ser positivos")
    void intervalsAndBatchSizesMustBePositive() {
        assertThatThrownBy(() -> settings(100, 50, 400, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(100, 50, 400, 20, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MatchmakingSettings settings(int initialRange, int step, int maxRange, int batchSize,
            int maxPairs) {
        return new MatchmakingSettings(true, Duration.ofSeconds(2), initialRange, step, Duration.ofSeconds(10),
                maxRange, Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(5), batchSize, maxPairs,
                Duration.ofSeconds(30));
    }
}
