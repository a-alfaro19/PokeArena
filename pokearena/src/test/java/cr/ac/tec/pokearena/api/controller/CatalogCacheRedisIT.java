package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.infrastructure.cache.RedisCatalogCache;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.RedisTestContainers;
import cr.ac.tec.pokearena.support.TestImages;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de la caché del catálogo con Redis real (sección 9).
 * Responsabilidades: con la aplicación completa, PostgreSQL y Redis en contenedores, verificar que las
 * páginas del catálogo del jugador y de la búsqueda del administrador se cachean en claves separadas y sin
 * datos del jugador, que Crear carta invalida la caché (la carta aparece sin esperar el vencimiento) y que
 * con Redis caído las tres operaciones siguen respondiendo bien.
 * Referencia: sección 9, sección 13 ("Redis caído no rompe nada") y criterios C.3 y C.6 ("una carta recién
 * creada puede tardar algunos segundos en aparecer").
 *
 * <p>Las pruebas corren en orden: la última detiene el contenedor de Redis de esta clase (no lo comparte
 * ninguna otra) para probar la falla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Caché del catálogo con Redis (API completa)")
class CatalogCacheRedisIT extends PostgresIntegrationTestBase {

    private static final GenericContainer<?> REDIS = RedisTestContainers.newRedis();
    private static final Path IMAGES_ROOT = createImagesRoot();

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("app.cache.redis-host", REDIS::getHost);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(RedisTestContainers.REDIS_PORT));
        registry.add("app.images.root-dir", IMAGES_ROOT::toString);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> players = new ArrayList<>();

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlayerAccountService accounts;
    @Autowired
    private TokenIssuer tokens;
    @Autowired
    private CatalogCache cache;
    @Autowired
    private StringRedisTemplate redis;

    private UUID player;
    private String playerToken;
    private String adminToken;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM card");
        player = TestPlayers.create(accounts).id();
        players.add(player);
        playerToken = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
        UUID admin = TestPlayers.create(accounts).id();
        players.add(admin);
        adminToken = tokens.issue(new AuthenticatedUser(admin, Role.ADMIN)).value();
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, players);
        players.clear();
        jdbc.update("DELETE FROM card");
    }

    @Test
    @Order(1)
    @DisplayName("9: con REDIS_HOST la aplicación usa Redis; catálogo y búsqueda se cachean en claves separadas y sin datos del jugador")
    void separateKeysWithoutPlayerData() throws Exception {
        assertThat(cache).isInstanceOf(RedisCatalogCache.class);

        assertThat(get("/api/v1/catalogo?type=FUE", playerToken).status()).isEqualTo(200);
        assertThat(get("/api/v1/cartas?type=FUE", adminToken).status()).isEqualTo(200);

        assertThat(redis.keys("catalog:v*:" + "player-catalog:*")).hasSize(1);
        assertThat(redis.keys("catalog:v*:" + "admin-search:*")).hasSize(1);
        assertThat(redis.keys("catalog:*")).noneMatch(key -> key.contains(player.toString()));
    }

    @Test
    @Order(2)
    @DisplayName("9, C.3 y C.6: la página queda en caché, y Crear carta la invalida: la carta nueva aparece sin esperar el vencimiento")
    void createCardInvalidates() throws Exception {
        // Se llenan las dos cachés con el catálogo vacío.
        assertThat(codes(get("/api/v1/catalogo", playerToken))).isEmpty();
        assertThat(codes(get("/api/v1/cartas", adminToken))).isEmpty();

        // Una fila insertada por fuera de la aplicación no invalida nada: prueba que la página viene de Redis.
        insertCard("PKA-AGU-900");
        assertThat(codes(get("/api/v1/catalogo", playerToken))).isEmpty();
        assertThat(codes(get("/api/v1/cartas", adminToken))).isEmpty();

        // Crear carta por la API invalida la caché después de confirmar.
        Result created = createCard();
        assertThat(created.status()).isEqualTo(201);
        String code = created.body().get("code").asString();

        assertThat(codes(get("/api/v1/catalogo", playerToken))).contains(code, "PKA-AGU-900");
        assertThat(codes(get("/api/v1/cartas", adminToken))).contains(code, "PKA-AGU-900");
    }

    @Test
    @Order(3)
    @DisplayName("13: con Redis caído, el catálogo, la búsqueda y Crear carta siguen respondiendo bien desde la base")
    void redisDownDoesNotBreakAnything() throws Exception {
        REDIS.stop();

        insertCard("PKA-PLA-900");
        Result catalog = get("/api/v1/catalogo", playerToken);
        Result search = get("/api/v1/cartas", adminToken);
        Result created = createCard();

        assertThat(catalog.status()).isEqualTo(200);
        assertThat(codes(catalog)).contains("PKA-PLA-900");
        assertThat(search.status()).isEqualTo(200);
        assertThat(codes(search)).contains("PKA-PLA-900");
        assertThat(created.status()).isEqualTo(201);
    }

    // =========================================================================================

    private void insertCard(String code) {
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, active, created_at)
                VALUES (?, ?, ?, 3, 1, ?, 'COMMON', 'HAB-000', ?, ?, 'READY', true, true, now())
                """, code, "Carta " + code, "carta " + code.toLowerCase(), code.substring(4, 7),
                "/images/cards/" + code + "/large.png", "/images/cards/" + code + "/small.png");
    }

    private Result createCard() throws IOException, InterruptedException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "Carta de caché");
        fields.put("cost", "3");
        fields.put("power", "4");
        fields.put("type", "FUE");
        fields.put("rarity", "COMMON");
        fields.put("ability", "HAB-000");
        String boundary = "limite-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"carta.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(TestImages.png(600, 840));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/cartas"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private Result get(String path, String bearer) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + bearer).GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private static List<String> codes(Result result) {
        assertThat(result.status()).as(result.body().toString()).isEqualTo(200);
        return result.body().get("items").valueStream().map(item -> item.get("code").asString()).toList();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static Path createImagesRoot() {
        try {
            return Files.createTempDirectory("pokearena-cache-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Result(int status, JsonNode body) {
    }
}
