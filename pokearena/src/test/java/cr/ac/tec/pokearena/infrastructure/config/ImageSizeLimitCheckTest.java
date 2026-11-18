package cr.ac.tec.pokearena.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: pruebas unitarias de {@link ImageSizeLimitCheck}.
 * Responsabilidades: comprobar que la aplicación arranca solo si el límite del multipart y el del dominio
 * coinciden.
 * Referencia: secciones 7.1 y 7.2 (paso 7) y regla "cero números mágicos".
 */
@DisplayName("Peso máximo de la imagen: una sola fuente")
class ImageSizeLimitCheckTest {

    @Test
    @DisplayName("7.1 y 7.2: con app.images.max-file-size igual a ImageRules.MAX_FILE_BYTES la aplicación arranca")
    void sameLimitStarts() {
        assertThatCode(() -> new ImageSizeLimitCheck(DataSize.ofBytes(ImageRules.MAX_FILE_BYTES)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("7.1 y 7.2: si app.images.max-file-size no coincide con el máximo del dominio, la aplicación no arranca")
    void differentLimitStops() {
        assertThatThrownBy(() -> new ImageSizeLimitCheck(DataSize.ofBytes(ImageRules.MAX_FILE_BYTES + 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.images.max-file-size")
                .hasMessageContaining("ImageRules.MAX_FILE_BYTES");
    }
}
