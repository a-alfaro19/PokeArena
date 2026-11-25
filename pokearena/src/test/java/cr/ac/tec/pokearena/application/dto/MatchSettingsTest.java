package cr.ac.tec.pokearena.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchSettingsTest {

    @Test
    @DisplayName("5.1.1: configuración válida conserva valores de partida")
    void acceptsValidSettings() {
        var settings = new MatchSettings(3, 2, 3, 7, 2, 1, 6, Duration.ofSeconds(30), 4);

        assertThat(settings.locationsPerMatch()).isEqualTo(3);
        assertThat(settings.turnDuration()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("5.1.1: configuración inválida no crea partida con límites incoherentes")
    void rejectsInvalidSettings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchSettings(2, 3, 3, 7, 2, 1, 6, Duration.ofSeconds(30), 4));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchSettings(3, 2, 3, 3, 2, 1, 6, Duration.ofSeconds(30), 4));
    }

    @Test
    @DisplayName("5.1.1: duración del turno es obligatoria")
    void requiresTurnDuration() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MatchSettings(3, 2, 3, 7, 2, 1, 6, null, 4));
    }
}
