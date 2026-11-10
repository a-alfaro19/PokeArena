package cr.ac.tec.pokearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de la entrega de la interfaz web (corrección 1; sección 11).
 * Responsabilidades: comprobar que la página se sirve en "/" y que sus archivos estáticos son accesibles
 * sin autenticación, que la configuración pública que la página lee al cargar está disponible, y que un
 * archivo estático inexistente responde 404 en el formato único.
 * Referencia: sección 11 ("servida por la misma API en http://localhost:8080") y sección 5.3.
 *
 * <p>Los archivos estáticos no son operaciones de un controlador: los atiende el manejador de recursos
 * de Spring, al que {@code AuthorizationInterceptor} no aplica la regla de "cerrado por defecto". Por eso
 * tampoco aparecen en la prueba de cobertura de rutas ni en la de contrato (que siguen pasando).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Interfaz web servida por la aplicación")
class StaticInterfaceIT extends PostgresIntegrationTestBase {

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("11: la página se sirve en / sin credenciales, con su título y sus archivos")
    void indexAtRoot() throws Exception {
        HttpResponse<String> response = get("/", "text/html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/html"));
        assertThat(response.body()).contains("<title>PokéArena", "app.js", "styles.css");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "/index.html, text/html", "/app.js, text/javascript", "/player.js, text/javascript", "/styles.css, text/css" })
    @DisplayName("11: los archivos estáticos son accesibles sin autenticación y con su tipo de contenido")
    void staticFilesArePublic(String path, String contentType) throws Exception {
        HttpResponse<String> response = get(path, "*/*");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith(contentType));
        assertThat(response.body()).isNotBlank();
    }

    @Test
    @DisplayName("11: la configuración que la interfaz lee al cargar es pública y responde con el formato esperado")
    void configurationForTheInterface() throws Exception {
        assertThat(get("/app.js", "*/*").body()).contains("/api/v1/configuracion");

        HttpResponse<String> response = get("/api/v1/configuracion", "application/json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).has("googleClientId")).isTrue();
    }

    @Test
    @DisplayName("5.3: un archivo estático inexistente responde 404 NOT_FOUND en el formato único")
    void missingStaticFile() throws Exception {
        HttpResponse<String> response = get("/no-existe.js", "*/*");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json.readTree(response.body()).get("type").asString()).isEqualTo("NOT_FOUND");
    }

    private HttpResponse<String> get(String path, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
