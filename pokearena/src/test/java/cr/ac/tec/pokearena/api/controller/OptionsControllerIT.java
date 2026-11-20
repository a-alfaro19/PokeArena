package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.effect.LocationEffectRegistry;
import cr.ac.tec.pokearena.domain.effect.LocationEffectType;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;
import cr.ac.tec.pokearena.domain.rule.DeckRules;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de las opciones de la interfaz ({@code GET /api/v1/opciones/catalogo}
 * y {@code GET /api/v1/opciones/creacion}).
 * Responsabilidades: verificar la autorización (401 sin sesión, 403 sin el rol), que el contenido sale de
 * la base y de las mismas clases que validan (sin valores copiados), y que el orden es estable.
 * Referencia: Anexos A.1, A.2, A.3 y A.6 (la interfaz pide las opciones antes del POST o del filtro).
 *
 * <p>Los valores esperados se leen de la base y de las constantes del dominio, no se escriben aquí: la
 * prueba comprueba que la API los expone, no repite los números.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Opciones de la interfaz desde la API (API completa)")
class OptionsControllerIT extends PostgresIntegrationTestBase {

    private static final String CATALOG = "/api/v1/opciones/catalogo";
    private static final String CREATION = "/api/v1/opciones/creacion";
    private static final String GAME = "/api/v1/opciones/juego";

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
    private LocationEffectRegistry effects;

    @Value("${app.pagination.default-limit}")
    private int defaultLimit;

    @Value("${app.pagination.max-limit}")
    private int maxLimit;

    private String playerToken;
    private String adminToken;

    @BeforeEach
    void sessions() {
        UUID player = TestPlayers.create(accounts).id();
        players.add(player);
        playerToken = tokens.issue(new AuthenticatedUser(player, Role.PLAYER)).value();
        adminToken = tokens.issue(new AuthenticatedUser(player, Role.ADMIN)).value();
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, players);
    }

    @Nested
    @DisplayName("Autorización")
    class Authorization {

        @Test
        @DisplayName("5.2: sin credenciales, ambas opciones responden 401 NOT_AUTHENTICATED")
        void withoutSession() throws Exception {
            for (String path : List.of(CATALOG, CREATION)) {
                Result result = get(path, null);
                assertThat(result.status()).as(path).isEqualTo(401);
                assertThat(result.body().get("type").asString()).isEqualTo("NOT_AUTHENTICATED");
            }
        }

        @Test
        @DisplayName("5.2: un jugador puede leer las opciones del catálogo, pero las de creación responden 403 FORBIDDEN")
        void playerOnlyCatalog() throws Exception {
            assertThat(get(CATALOG, playerToken).status()).isEqualTo(200);

            Result creation = get(CREATION, playerToken);
            assertThat(creation.status()).isEqualTo(403);
            assertThat(creation.body().get("type").asString()).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("5.2: un administrador lee ambas opciones")
        void adminBoth() throws Exception {
            assertThat(get(CATALOG, adminToken).status()).isEqualTo(200);
            assertThat(get(CREATION, adminToken).status()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Opciones del catálogo")
    class CatalogOptions {

        @Test
        @DisplayName("Tipos y rarezas salen de card_type y rarity, ordenados por código")
        void typesAndRaritiesFromDatabase() throws Exception {
            JsonNode body = get(CATALOG, playerToken).body();

            assertThat(codes(body.get("types")))
                    .isEqualTo(jdbc.queryForList("SELECT code FROM card_type ORDER BY code", String.class));
            assertThat(codes(body.get("rarities")))
                    .isEqualTo(jdbc.queryForList("SELECT code FROM rarity ORDER BY code", String.class));
            assertThat(body.get("types").get(0).get("name").asString())
                    .isEqualTo(jdbc.queryForObject("SELECT name FROM card_type ORDER BY code LIMIT 1", String.class));
        }

        @Test
        @DisplayName("El rango de costo es el de CardFieldRules y la paginación la de app.pagination.*")
        void costAndPagination() throws Exception {
            JsonNode body = get(CATALOG, playerToken).body();

            assertRange(body.get("cost"), CardFieldRules.MIN_COST, CardFieldRules.MAX_COST);
            assertThat(body.get("pagination").get("defaultLimit").asInt()).isEqualTo(defaultLimit);
            assertThat(body.get("pagination").get("maxLimit").asInt()).isEqualTo(maxLimit);
            // El mínimo aceptado es el que la paginación acepta: un limit menor es INVALID_PAGINATION.
            int minLimit = body.get("pagination").get("minLimit").asInt();
            assertThat(get("/api/v1/catalogo?limit=" + minLimit, playerToken).status()).isEqualTo(200);
            assertThat(get("/api/v1/catalogo?limit=" + (minLimit - 1), playerToken).body().get("type").asString())
                    .isEqualTo("INVALID_PAGINATION");
        }

        @Test
        @DisplayName("El orden es estable: dos solicitudes devuelven exactamente lo mismo")
        void stableOrder() throws Exception {
            assertThat(get(CATALOG, playerToken).body()).isEqualTo(get(CATALOG, playerToken).body());
        }
    }

    @Nested
    @DisplayName("Opciones de creación")
    class CreationOptions {

        @Test
        @DisplayName("Las habilidades son las activas de la tabla ability, por código, e incluyen HAB-000 \"Sin Habilidad\"")
        void activeAbilities() throws Exception {
            JsonNode body = get(CREATION, adminToken).body();

            assertThat(codes(body.get("abilities"))).isEqualTo(
                    jdbc.queryForList("SELECT code FROM ability WHERE active ORDER BY code", String.class))
                    .contains(Ability.NO_ABILITY_CODE);
        }

        @Test
        @DisplayName("Los rangos de costo, poder y límite de cartas son los de CardFieldRules y LocationFieldRules")
        void ranges() throws Exception {
            JsonNode body = get(CREATION, adminToken).body();

            assertRange(body.get("card").get("cost"), CardFieldRules.MIN_COST, CardFieldRules.MAX_COST);
            assertRange(body.get("card").get("power"), CardFieldRules.MIN_POWER, CardFieldRules.MAX_POWER);
            assertRange(body.get("location").get("cardLimit"), LocationFieldRules.MIN_CARD_LIMIT,
                    LocationFieldRules.MAX_CARD_LIMIT);
        }

        @Test
        @DisplayName("7.2: las modalidades de energía son las de ExtraEnergy.Mode, con su maxAmount, y NONE por defecto")
        void energyModes() throws Exception {
            JsonNode energy = get(CREATION, adminToken).body().get("location").get("extraEnergy");

            assertThat(energy.get("defaultMode").asString()).isEqualTo(ExtraEnergy.DEFAULT_MODE.name());
            List<ExtraEnergy.Mode> modes = List.of(ExtraEnergy.Mode.values());
            assertThat(codes(energy.get("modes"))).isEqualTo(modes.stream().map(Enum::name).toList());
            for (int i = 0; i < modes.size(); i++) {
                assertThat(energy.get("modes").get(i).get("minAmount").asInt()).isEqualTo(ExtraEnergy.MIN_AMOUNT);
                assertThat(energy.get("modes").get(i).get("maxAmount").asInt()).isEqualTo(modes.get(i).maxAmount());
            }
        }

        @Test
        @DisplayName("7.2: los efectos son los del registro, por código, con la descripción de sus parámetros")
        void effects() throws Exception {
            JsonNode body = get(CREATION, adminToken).body().get("location").get("effects");

            List<LocationEffectType> registered = effects.types();
            assertThat(codes(body)).isEqualTo(registered.stream().map(LocationEffectType::code).toList());
            for (int i = 0; i < registered.size(); i++) {
                JsonNode effect = body.get(i);
                assertThat(effect.get("name").asString()).isEqualTo(registered.get(i).name());
                assertThat(effect.get("parameters").size()).isEqualTo(registered.get(i).parameters().size());
                for (int j = 0; j < registered.get(i).parameters().size(); j++) {
                    var spec = registered.get(i).parameters().get(j);
                    JsonNode parameter = effect.get("parameters").get(j);
                    assertThat(parameter.get("name").asString()).isEqualTo(spec.name());
                    assertThat(parameter.get("kind").asString()).isEqualTo(spec.kind().name());
                    assertThat(parameter.get("required").asBoolean()).isEqualTo(spec.required());
                    // min y max siempre presentes; null con CARD_TYPE.
                    assertThat(parameter.has("min")).isTrue();
                    assertThat(parameter.get("min").isNull()).isEqualTo(spec.min() == null);
                }
            }
        }

        @Test
        @DisplayName("Los requisitos de imagen son los de ImageRules (formatos, peso, proporción y resolución mínima)")
        void images() throws Exception {
            JsonNode images = get(CREATION, adminToken).body().get("images");

            assertThat(images.get("maxFileBytes").asLong()).isEqualTo(ImageRules.MAX_FILE_BYTES);
            assertThat(images.get("formats")).hasSize(ImageRules.ALLOWED_FORMATS.size());
            assertImage(images.get("card"), ImageRules.CARD);
            assertImage(images.get("location"), ImageRules.LOCATION);
        }

        @Test
        @DisplayName("El orden es estable: dos solicitudes devuelven exactamente lo mismo")
        void stableOrder() throws Exception {
            assertThat(get(CREATION, adminToken).body()).isEqualTo(get(CREATION, adminToken).body());
        }
    }

    @Nested
    @DisplayName("Opciones del juego (Sprint 3, sección 7.4)")
    class GameOptions {

        @Test
        @DisplayName("5.2: sin credenciales responde 401 NOT_AUTHENTICATED; un jugador y un administrador la leen")
        void authorization() throws Exception {
            Result anonymous = get(GAME, null);
            assertThat(anonymous.status()).isEqualTo(401);
            assertThat(anonymous.body().get("type").asString()).isEqualTo("NOT_AUTHENTICATED");

            assertThat(get(GAME, playerToken).status()).isEqualTo(200);
            assertThat(get(GAME, adminToken).status()).isEqualTo(200);
        }

        @Test
        @DisplayName("7.4: deckSize y deckNameMaxLength son los de DeckRules, no valores copiados")
        void deckRules() throws Exception {
            JsonNode body = get(GAME, playerToken).body();

            assertThat(body.get("deckSize").asInt()).isEqualTo(DeckRules.DECK_SIZE);
            assertThat(body.get("deckNameMaxLength").asInt()).isEqualTo(DeckRules.NAME_MAX_LENGTH);
        }
    }

    // =========================================================================================

    private static List<String> codes(JsonNode array) {
        List<String> codes = new ArrayList<>();
        array.forEach(node -> codes.add(node.get("code").asString()));
        return codes;
    }

    private static void assertRange(JsonNode range, int min, int max) {
        assertThat(range.get("min").asInt()).isEqualTo(min);
        assertThat(range.get("max").asInt()).isEqualTo(max);
    }

    private static void assertImage(JsonNode image, ImageRules rules) {
        assertThat(image.get("ratioWidth").asInt()).isEqualTo(rules.ratioWidth());
        assertThat(image.get("ratioHeight").asInt()).isEqualTo(rules.ratioHeight());
        assertThat(image.get("minWidth").asInt()).isEqualTo(rules.minWidth());
        assertThat(image.get("minHeight").asInt()).isEqualTo(rules.minHeight());
    }

    private Result get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private record Result(int status, JsonNode body) {
    }
}
