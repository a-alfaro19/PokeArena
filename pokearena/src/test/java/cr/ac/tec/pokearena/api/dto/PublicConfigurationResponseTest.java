package cr.ac.tec.pokearena.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Propósito: comprobar cómo {@code GET /api/v1/configuracion} informa el {@code client id} (sección 11).
 * Responsabilidades: verificar que un valor ausente o vacío es {@code null} y que un valor real se
 * entrega sin espacios sobrantes.
 * Referencia: sección 11.
 */
@DisplayName("Configuración pública: client id de Google")
class PublicConfigurationResponseTest {

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    @DisplayName("Sin client id configurado, googleClientId es null")
    void blankIsNull(String configured) {
        assertThat(PublicConfigurationResponse.of(configured).googleClientId()).isNull();
    }

    @Test
    @DisplayName("Con client id configurado, lo entrega sin espacios")
    void configuredValue() {
        assertThat(PublicConfigurationResponse.of(" 123-abc.apps.googleusercontent.com ").googleClientId())
                .isEqualTo("123-abc.apps.googleusercontent.com");
    }
}
