package cr.ac.tec.pokearena.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.infrastructure.identity.JwtTokenService;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestSecrets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar de punta a punta la autorización con credenciales propias (sección 5.2).
 * Responsabilidades: comprobar con el servidor real que sin token, con un token inválido,
 * manipulado o vencido la respuesta es 401 NOT_AUTHENTICATED; que con un token válido sin el rol
 * es 403 FORBIDDEN; y que con el rol correcto el controlador recibe al usuario autenticado.
 * Referencia: sección 5.2 y criterios "401 contra 403" de C.1, C.2 y C.3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ AuthorizationIT.ProtectedTestController.class, AuthorizationIT.AdminOnlyTestController.class })
@DisplayName("Autorización con credenciales propias: 401 contra 403")
class AuthorizationIT extends PostgresIntegrationTestBase {

    private static final String BASE_PATH = "/test/seguridad";

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Test
    @DisplayName("Con un token válido de ADMIN, la operación de administrador responde y recibe al usuario")
    void validAdminTokenIsAccepted() throws Exception {
        UUID adminId = UUID.randomUUID();

        Result result = get("/admin", bearer(Role.ADMIN, adminId));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("playerId").asString()).isEqualTo(adminId.toString());
        assertThat(result.body().get("role").asString()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Sin token, una operación protegida responde 401 NOT_AUTHENTICATED")
    void missingTokenIsNotAuthenticated() throws Exception {
        assertError(get("/admin", null), 401, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("Un encabezado Authorization que no es Bearer responde 401 NOT_AUTHENTICATED")
    void otherSchemeIsNotAuthenticated() throws Exception {
        assertError(get("/admin", "Basic dXN1YXJpbzpjbGF2ZQ=="), 401, "NOT_AUTHENTICATED");
        assertError(get("/admin", "Bearer "), 401, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("Un token que no es un JWT responde 401 NOT_AUTHENTICATED")
    void garbageTokenIsNotAuthenticated() throws Exception {
        assertError(get("/admin", "Bearer no-es-un-token"), 401, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("Un token vencido responde 401 NOT_AUTHENTICATED")
    void expiredTokenIsNotAuthenticated() throws Exception {
        // Mismo secreto que la aplicación, pero emitido hace una hora con 15 minutos de validez.
        JwtTokenService pastIssuer = new JwtTokenService(TEST_JWT_SECRET, Duration.ofMinutes(15),
                () -> Instant.now().minus(Duration.ofHours(1)));
        String token = pastIssuer.issue(new AuthenticatedUser(UUID.randomUUID(), Role.ADMIN)).value();

        assertError(get("/admin", "Bearer " + token), 401, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("Un token firmado con otro secreto responde 401 NOT_AUTHENTICATED")
    void tokenWithOtherSignatureIsNotAuthenticated() throws Exception {
        JwtTokenService otherIssuer = new JwtTokenService(TestSecrets.randomJwtSecret(), Duration.ofMinutes(15),
                Instant::now);
        String token = otherIssuer.issue(new AuthenticatedUser(UUID.randomUUID(), Role.ADMIN)).value();

        assertError(get("/admin", "Bearer " + token), 401, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("Un token de PLAYER con el rol manipulado a ADMIN responde 401, no 200 ni 403")
    void tamperedRoleIsNotAuthenticated() throws Exception {
        String token = tokenIssuer.issue(new AuthenticatedUser(UUID.randomUUID(), Role.PLAYER)).value();
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"PLAYER\"", "\"ADMIN\"").getBytes());

        assertError(get("/admin", "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2]),
                401, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("Un token válido de PLAYER en una operación de administrador responde 403 FORBIDDEN")
    void playerOnAdminOperationIsForbidden() throws Exception {
        assertError(get("/admin", bearer(Role.PLAYER, UUID.randomUUID())), 403, "FORBIDDEN");
    }

    @Test
    @DisplayName("401 y 403 son errores distintos: sin token frente a token sin el rol")
    void notAuthenticatedAndForbiddenAreDistinct() throws Exception {
        Result withoutToken = get("/admin", null);
        Result withoutRole = get("/admin", bearer(Role.PLAYER, UUID.randomUUID()));

        assertThat(withoutToken.status()).isNotEqualTo(withoutRole.status());
        assertThat(withoutToken.body().get("type").asString()).isNotEqualTo(withoutRole.body().get("type").asString());
    }

    @Test
    @DisplayName("Una operación de jugador acepta PLAYER y rechaza ADMIN si no lo declara")
    void roleMustBeDeclared() throws Exception {
        assertThat(get("/jugador", bearer(Role.PLAYER, UUID.randomUUID())).status()).isEqualTo(200);
        assertError(get("/jugador", bearer(Role.ADMIN, UUID.randomUUID())), 403, "FORBIDDEN");
    }

    @Test
    @DisplayName("Una operación puede aceptar varios roles")
    void severalRolesCanBeAccepted() throws Exception {
        assertThat(get("/cualquiera", bearer(Role.PLAYER, UUID.randomUUID())).status()).isEqualTo(200);
        assertThat(get("/cualquiera", bearer(Role.ADMIN, UUID.randomUUID())).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("@RequiresRole sobre la clase protege todas sus operaciones")
    void classLevelAnnotationProtects() throws Exception {
        assertError(get("/solo-admin/operacion", null), 401, "NOT_AUTHENTICATED");
        assertError(get("/solo-admin/operacion", bearer(Role.PLAYER, UUID.randomUUID())), 403, "FORBIDDEN");
        assertThat(get("/solo-admin/operacion", bearer(Role.ADMIN, UUID.randomUUID())).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Una operación con @PublicRoute es pública y no exige token")
    void publicRouteDoesNotRequireToken() throws Exception {
        assertThat(get("/publico", null).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Una operación sin @RequiresRole ni @PublicRoute se rechaza con 403, aun con token de ADMIN")
    void undeclaredOperationIsClosed() throws Exception {
        assertError(get("/sin-anotacion", null), 403, "FORBIDDEN");
        assertError(get("/sin-anotacion", bearer(Role.ADMIN, UUID.randomUUID())), 403, "FORBIDDEN");
        // Se rechaza antes de ejecutar el método.
        assertThat(ProtectedTestController.undeclaredCalls.get()).isZero();
    }

    @Test
    @DisplayName("@PublicRoute en un método manda sobre @RequiresRole de su clase")
    void methodPublicRouteOverridesClassRole() throws Exception {
        assertThat(get("/solo-admin/estado", null).status()).isEqualTo(200);
    }

    // ---------------------------------------------------------------------------------------
    // Controladores SOLO de prueba, anidados para que Spring no los encuentre al escanear.
    // ---------------------------------------------------------------------------------------

    @RestController
    @RequestMapping(BASE_PATH)
    static class ProtectedTestController {

        @GetMapping("/admin")
        @RequiresRole(Role.ADMIN)
        Map<String, String> admin(AuthenticatedUser user) {
            return Map.of("playerId", user.playerId().toString(), "role", user.role().name());
        }

        @GetMapping("/jugador")
        @RequiresRole(Role.PLAYER)
        String player() {
            return "ok";
        }

        @GetMapping("/cualquiera")
        @RequiresRole({ Role.PLAYER, Role.ADMIN })
        String anyRole() {
            return "ok";
        }

        @GetMapping("/publico")
        @PublicRoute
        String open() {
            return "ok";
        }

        // Cuenta las ejecuciones, para comprobar que la operación sin anotación nunca se ejecuta.
        static final AtomicInteger undeclaredCalls = new AtomicInteger();

        @GetMapping("/sin-anotacion")
        String undeclared() {
            undeclaredCalls.incrementAndGet();
            return "no debería ejecutarse";
        }
    }

    @RestController
    @RequestMapping(BASE_PATH + "/solo-admin")
    @RequiresRole(Role.ADMIN)
    static class AdminOnlyTestController {

        @GetMapping("/operacion")
        String operation() {
            return "ok";
        }

        @GetMapping("/estado")
        @PublicRoute
        String status() {
            return "ok";
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes.
    // ---------------------------------------------------------------------------------------

    private String bearer(Role role, UUID playerId) {
        return "Bearer " + tokenIssuer.issue(new AuthenticatedUser(playerId, role)).value();
    }

    private Result get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE_PATH + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        HttpResponse<String> response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = response.body().startsWith("{") ? json.readTree(response.body()) : json.createObjectNode();
        return new Result(response.statusCode(), body);
    }

    private static void assertError(Result result, int status, String type) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
        assertThat(result.body().get("status").asInt()).isEqualTo(status);
        assertThat(result.body().get("correlationId").asString()).isNotBlank();
    }

    private record Result(int status, JsonNode body) {
    }
}
