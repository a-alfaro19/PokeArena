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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenVerifier;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar {@code POST /api/v1/dev/sesiones} con el perfil dev (sección 5.2).
 * Responsabilidades: comprobar que emite credenciales válidas de ADMIN y PLAYER, que pertenecen a
 * jugadores reales con proveedor dev, que dos llamadas con el mismo rol reutilizan el mismo jugador
 * y que un rol ausente o desconocido se rechaza con el formato único.
 * Referencia: sección 5.2 y sección 6 (las credenciales de prueba crean jugadores reales).
 *
 * <p>El perfil dev cambia los logs a texto, y Spring Boot guarda esa configuración en propiedades
 * del sistema, que comparte toda la JVM de las pruebas. Para no cambiar el formato de los logs de
 * las demás pruebas (algunas verifican el JSON), aquí se mantiene el logging predeterminado: esta
 * prueba verifica el endpoint, no el formato de los logs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "logging.structured.format.console=ecs", "logging.pattern.level=%5p" })
@ActiveProfiles("dev")
@DisplayName("Credenciales de prueba con el perfil dev")
class DevSessionControllerIT extends PostgresIntegrationTestBase {

    private static final String PATH = "/api/v1/dev/sesiones";

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private TokenVerifier tokenVerifier;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Con el perfil dev, pedir ADMIN devuelve credenciales válidas de un administrador real")
    void issuesAdminCredentials() throws Exception {
        JsonNode body = post("{\"role\":\"ADMIN\"}").body();

        AuthenticatedUser user = tokenVerifier.verify(body.get("accessToken").asString());
        assertThat(user.role()).isEqualTo(Role.ADMIN);
        assertThat(user.playerId().toString()).isEqualTo(body.get("player").get("id").asString());
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isPositive();
        // El sub del token es un jugador que existe, asociado a la identidad dev:dev-admin.
        assertThat(playerOf("dev-admin")).isEqualTo(user.playerId().toString());
    }

    @Test
    @DisplayName("Con el perfil dev, pedir PLAYER devuelve credenciales de un jugador real")
    void issuesPlayerCredentials() throws Exception {
        JsonNode body = post("{\"role\":\"PLAYER\"}").body();

        AuthenticatedUser user = tokenVerifier.verify(body.get("accessToken").asString());
        assertThat(user.role()).isEqualTo(Role.PLAYER);
        assertThat(playerOf("dev-player")).isEqualTo(user.playerId().toString());
    }

    @Test
    @DisplayName("Dos llamadas con el mismo rol reutilizan el mismo jugador: una sola fila")
    void sameRoleReusesPlayer() throws Exception {
        String first = post("{\"role\":\"ADMIN\"}").body().get("player").get("id").asString();
        String second = post("{\"role\":\"ADMIN\"}").body().get("player").get("id").asString();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM player_identity WHERE provider = 'dev' AND provider_subject = 'dev-admin'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player WHERE id = ?::uuid", Integer.class, first))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Sin rol responde MISSING_FIELD y con un rol desconocido INVALID_FIELD con los valores permitidos")
    void invalidRoleIsRejected() throws Exception {
        Result missing = post("{}");
        Result unknown = post("{\"role\":\"SUPERUSUARIO\"}");

        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().get("type").asString()).isEqualTo("MISSING_FIELD");
        assertThat(missing.body().get("field").asString()).isEqualTo("role");
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.body().get("type").asString()).isEqualTo("INVALID_FIELD");
        assertThat(unknown.body().get("allowedValues").toString()).contains("PLAYER", "ADMIN");
    }

    private String playerOf(String subject) {
        return jdbc.queryForObject("""
                SELECT player_id::text FROM player_identity WHERE provider = 'dev' AND provider_subject = ?
                """, String.class, subject);
    }

    private Result post(String body) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private record Result(int status, JsonNode body) {
    }
}
