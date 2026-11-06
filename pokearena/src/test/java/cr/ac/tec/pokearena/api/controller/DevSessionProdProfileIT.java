package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import cr.ac.tec.pokearena.application.usecase.IssueDevSessionUseCase;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar que las credenciales de prueba NO existen con el perfil prod (RNF-SEG-10).
 * Responsabilidades: comprobar que {@code POST /api/v1/dev/sesiones} responde 404 con el formato
 * único y que ni su controlador ni su caso de uso se crean.
 * Referencia: sección 5.2 y sección 13 ("dev/sesiones no existe con el perfil prod").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@DisplayName("Credenciales de prueba con el perfil prod")
class DevSessionProdProfileIT extends PostgresIntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Con el perfil prod, POST /api/v1/dev/sesiones no existe y responde 404 NOT_FOUND")
    void devSessionsDoNotExistInProd() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/dev/sesiones"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"role\":\"ADMIN\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode body = JsonMapper.builder().build().readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(body.get("type").asString()).isEqualTo("NOT_FOUND");
        assertThat(body.has("accessToken")).isFalse();
    }

    @Test
    @DisplayName("Con el perfil prod no se crean ni el controlador ni el caso de uso de prueba")
    void devBeansAreAbsentInProd() {
        assertThat(context.getBeanNamesForType(DevSessionController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(IssueDevSessionUseCase.class)).isEmpty();
    }
}
