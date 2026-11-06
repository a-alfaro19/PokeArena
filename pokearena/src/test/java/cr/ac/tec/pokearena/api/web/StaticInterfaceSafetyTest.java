package cr.ac.tec.pokearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Propósito: revisión automática de las reglas de seguridad de la interfaz web (sección 11).
 * Responsabilidades: impedir que vuelvan a aparecer en {@code app.js} las APIs que insertan HTML
 * (riesgo de inyección con texto del servidor) o que guardan el token en el navegador, y que la página
 * cargue recursos de otro origen (bibliotecas o CDN).
 * Referencia: sección 11 y las reglas de la tarea de la interfaz (token solo en memoria, sin innerHTML,
 * sin bibliotecas externas).
 *
 * <p>Es una revisión de texto, no un análisis completo de JavaScript: busca los usos, no las menciones
 * en comentarios (por eso exige el punto o el paréntesis de la llamada).
 */
@DisplayName("Reglas de seguridad de la interfaz web")
class StaticInterfaceSafetyTest {

    private static String read(String file) {
        try (InputStream input = StaticInterfaceSafetyTest.class.getResourceAsStream("/static/" + file)) {
            assertThat(input).as("existe static/%s", file).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "\\.innerHTML\\b", "\\.outerHTML\\b", "insertAdjacentHTML\\s*\\(", "document\\.write\\s*\\(",
            "\\beval\\s*\\(", "new Function\\s*\\(" })
    @DisplayName("app.js no inserta HTML ni ejecuta texto: solo textContent y nodos creados a mano")
    void noHtmlInjection(String forbidden) {
        assertThat(Pattern.compile(forbidden).matcher(read("app.js")).find()).as(forbidden).isFalse();
    }

    @Test
    @DisplayName("El token vive solo en memoria: app.js no usa localStorage, sessionStorage ni cookies")
    void tokenOnlyInMemory() {
        assertThat(read("app.js")).doesNotContain("localStorage", "sessionStorage", "document.cookie");
    }

    @Test
    @DisplayName("Sin bibliotecas ni CDN: la página solo carga recursos del mismo origen")
    void sameOriginOnly() {
        String index = read("index.html");
        assertThat(Pattern.compile("(src|href)\\s*=\\s*\"(https?:)?//").matcher(index).find()).isFalse();
        assertThat(index).contains("default-src 'self'");
        assertThat(Pattern.compile("fetch\\(\\s*['\"`](https?:)?//").matcher(read("app.js")).find()).isFalse();
        assertThat(read("styles.css")).doesNotContain("@import", "url(http");
    }
}
