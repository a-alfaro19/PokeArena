package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar {@code GET /api/v1/configuracion} con la aplicación completa (sección 11).
 * Responsabilidades: comprobar que es pública, que sin {@code app.identity.google.client-id} informa
 * {@code googleClientId: null} (la interfaz deja el botón de Google deshabilitado) y que no expone nada más.
 * Referencia: sección 11 (pantalla de ingreso).
 *
 * <p>Usa la configuración por defecto de las pruebas, sin {@code client id}. El caso con un {@code client
 * id} configurado lo cubre {@code SessionControllerIT}, que ya arranca la aplicación con uno, y la
 * normalización del valor la cubre {@code PublicConfigurationResponseTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Configuración pública de la interfaz")
class ConfigurationControllerIT extends PostgresIntegrationTestBase {

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("11: sin credenciales responde 200; sin client id configurado, googleClientId es null y no hay otros campos")
    void publicAndNullWithoutClientId() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/configuracion")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.propertyNames()).containsExactly("googleClientId");
        assertThat(body.get("googleClientId").isNull()).isTrue();
    }
}
