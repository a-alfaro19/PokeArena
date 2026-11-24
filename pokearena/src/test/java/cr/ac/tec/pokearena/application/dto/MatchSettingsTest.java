package cr.ac.tec.pokearena.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propósito: comprobar que la configuración del juego no permite reglas incoherentes.
 * Responsabilidades: validar relaciones entre límites de ubicaciones, mano, turnos y apuesta antes de
 * entregarlos a las reglas de dominio.
 * Referencia: secciones 4.2 y 5.7.
 */
@DisplayName("Configuración de partidas")
class MatchSettingsTest {

    @Test
    @DisplayName("Un mínimo de ubicaciones jugables mayor que el total configurado no se acepta")
    void playableMinimumCannotExceedLocationCount() {
        assertThatThrownBy(() -> settings(3, 4, 3, 7, Duration.ofSeconds(30), 4, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La mano inicial no puede superar el tope de mano")
    void initialHandCannotExceedMaximum() {
        assertThatThrownBy(() -> settings(3, 2, 8, 7, Duration.ofSeconds(30), 4, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La duración de turno y los límites de apuesta deben ser válidos")
    void durationAndBetMustBeValid() {
        assertThatThrownBy(() -> settings(3, 2, 3, 7, Duration.ZERO, 4, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(3, 2, 3, 7, Duration.ofSeconds(30), 8, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MatchSettings settings(int locations, int playable, int hand, int maxHand, Duration duration,
            int bet, int maxBet) {
        return new MatchSettings(locations, playable, hand, maxHand, 2, 1, 6, duration, bet, maxBet);
    }
}
