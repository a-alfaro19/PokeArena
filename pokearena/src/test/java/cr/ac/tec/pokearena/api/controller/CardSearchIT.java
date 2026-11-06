package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.NameNormalizer;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de la historia 2.1.2 "Buscar carta", una por cada criterio de C.3.
 * Responsabilidades: con el servidor real y PostgreSQL de pruebas, comprobar cada criterio y sus
 * combinaciones, la ficha de cada resultado, 401 contra 403, cada error con su status, type y field, la
 * paginación única (5.5) y el orden estable. El rendimiento con 10.000 cartas está en
 * {@code CardSearchPerformanceIT}.
 * Referencia: Anexo C.3, sección 7.3, sección 5.5 y Anexo A.3.
 *
 * <p>Los casos de los filtros, de los comodines y de los errores se portaron de
 * {@code CardSearchPostgresTests} del código original del Sprint 1 (etiqueta {@code sprint1-original}), sin las instantáneas, los cursores UUID, {@code total}
 * ni {@code expiresAt}. Las cartas se insertan directo en la base (aquí no se prueba Crear carta) y
 * cada prueba deja la base como estaba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Buscar carta (2.1.2) de punta a punta")
class CardSearchIT extends PostgresIntegrationTestBase {

    private static final String PATH = "/api/v1/cartas";
    private static final String TEST_ABILITY = "HAB-T-REVELAR";
    private static final int FIXTURE_CARDS = 5;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> createdPlayers = new ArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private TokenIssuer tokenIssuer;

    private String admin;

    @BeforeEach
    void prepare() {
        resetCatalog();
        // Habilidad de prueba con disparador, efecto, parámetros y tipo limitado (sección 14: solo en
        // los datos de prueba).
        jdbc.update("""
                INSERT INTO ability (code, name, trigger, effect_type, parameters, limited_to_type, created_at)
                VALUES (?, 'Revelación', 'AL_REVELAR', 'BONUS_PODER', '{"amount": 2}'::jsonb, 'AGU', now())
                """, TEST_ABILITY);
        // Portadas del Sprint 1 original. El orden de inserción es el orden de id, y por lo tanto el de los resultados.
        card("PKA-FUE-001", "Dragón Áureo", 0, -20, "FUE", "COMMON", "HAB-000", true);
        card("PKA-AGU-001", "Dragón Azul", 5, 10, "AGU", "RARE", TEST_ABILITY, true);
        card("PKA-ELE-001", "100%_Volt!", 10, 20, "ELE", "LEGENDARY", TEST_ABILITY, true);
        card("PKA-PLA-001", "Bosque", 3, 0, "PLA", "UNCOMMON", "HAB-000", false);
        card("PKA-PSI-001", "Comilla ' y barra \\ literal", 5, 5, "PSI", "COMMON", "HAB-000", true);
        admin = token(Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        resetCatalog();
        TestPlayers.delete(jdbc, createdPlayers);
    }

    // =========================================================================================
    // Criterios de éxito (C.3)
    // =========================================================================================

    @Nested
    @DisplayName("Éxito")
    class Success {

        @ParameterizedTest(name = "{0}={1}: {2} carta(s)")
        @CsvSource({ "code, PKA-FUE-001, 1", "name, dragon, 2", "name, ÁUREO, 1", "name, azu, 1",
                "cost, 5, 2", "power, 0, 1", "type, AGU, 1", "rarity, COMMON, 2", "ability, HAB-000, 3",
                "ability, HAB-T-REVELAR, 2", "abilityTrigger, AL_REVELAR, 2", "abilityEffect, BONUS_PODER, 2" })
        @DisplayName("C.3 éxito: se puede buscar por código, nombre (parcial, sin mayúsculas ni tildes), costo, poder, tipo, rareza y habilidad (por código, incluida Sin Habilidad, o por disparador y efecto)")
        void eachCriterion(String criterion, String value, int expected) throws Exception {
            assertThat(search(criterion + "=" + encode(value)).get("items").size()).isEqualTo(expected);
        }

        @Test
        @DisplayName("C.3 éxito: los criterios se combinan y los resultados cumplen todos los criterios activos a la vez")
        void combinedCriteria() throws Exception {
            JsonNode page = search("name=DRAGON&costMin=4&costMax=6&powerMin=0&powerMax=15&type=AGU&rarity=RARE"
                    + "&ability=HAB-T-REVELAR&abilityTrigger=AL_REVELAR&abilityEffect=BONUS_PODER");

            assertThat(codes(page)).containsExactly("PKA-AGU-001");
            // Cada criterio por separado encuentra más cartas: el resultado es la intersección (AND).
            assertThat(codes(search("name=dragon&type=FUE"))).containsExactly("PKA-FUE-001");
            // Sin Habilidad no tiene disparador: HAB-000 con un disparador no encuentra nada.
            assertThat(codes(search("ability=HAB-000&abilityTrigger=AL_REVELAR"))).isEmpty();
        }

        @ParameterizedTest(name = "{0}: {1}")
        @CsvSource(delimiter = '|', value = { "cost=5 | PKA-AGU-001,PKA-PSI-001", "costMin=5 | PKA-AGU-001,PKA-ELE-001,PKA-PSI-001",
                "costMax=3 | PKA-FUE-001,PKA-PLA-001", "costMin=3&costMax=5 | PKA-AGU-001,PKA-PLA-001,PKA-PSI-001",
                "cost=0 | PKA-FUE-001", "cost=10 | PKA-ELE-001", "power=-20 | PKA-FUE-001", "power=20 | PKA-ELE-001",
                "powerMin=10 | PKA-AGU-001,PKA-ELE-001", "powerMax=0 | PKA-FUE-001,PKA-PLA-001",
                "powerMin=-5&powerMax=5 | PKA-PLA-001,PKA-PSI-001" })
        @DisplayName("C.3 éxito: costo y poder aceptan un valor exacto o un rango (también abierto y en los extremos)")
        void exactValueOrRange(String query, String expected) throws Exception {
            assertThat(codes(search(query))).containsExactly(expected.split(","));
        }

        @Test
        @DisplayName("C.3 éxito: cada resultado muestra código, nombre, imagen, estado de la imagen, costo, poder, tipo, rareza y habilidad con sus parámetros")
        void resultFields() throws Exception {
            JsonNode card = search("code=PKA-AGU-001").get("items").get(0);

            assertThat(card.get("code").asString()).isEqualTo("PKA-AGU-001");
            assertThat(card.get("name").asString()).isEqualTo("Dragón Azul");
            assertThat(card.get("imageLargeUrl").asString()).isEqualTo("/images/cards/PKA-AGU-001/large.png");
            assertThat(card.get("imageSmallUrl").asString()).isEqualTo("/images/cards/PKA-AGU-001/small.png");
            assertThat(card.get("imageStatus").asString()).isEqualTo("READY");
            assertThat(card.get("available").asBoolean()).isTrue();
            assertThat(card.get("cost").asInt()).isEqualTo(5);
            assertThat(card.get("power").asInt()).isEqualTo(10);
            assertThat(card.get("type").get("code").asString()).isEqualTo("AGU");
            assertThat(card.get("type").get("name").asString()).isEqualTo("Agua");
            assertThat(card.get("rarity").get("code").asString()).isEqualTo("RARE");
            assertThat(card.get("rarity").get("name").asString()).isEqualTo("Rara");
            JsonNode ability = card.get("ability");
            assertThat(ability.get("code").asString()).isEqualTo(TEST_ABILITY);
            assertThat(ability.get("name").asString()).isEqualTo("Revelación");
            assertThat(ability.get("trigger").asString()).isEqualTo("AL_REVELAR");
            assertThat(ability.get("effect").asString()).isEqualTo("BONUS_PODER");
            assertThat(ability.get("limitedToType").asString()).isEqualTo("AGU");
            assertThat(ability.get("parameters").get("amount").asInt()).isEqualTo(2);

            // Sin Habilidad: sin disparador ni efecto, y parámetros vacíos (no null).
            JsonNode none = search("code=PKA-FUE-001").get("items").get(0).get("ability");
            assertThat(none.get("name").asString()).isEqualTo("Sin Habilidad");
            assertThat(none.get("trigger").isNull()).isTrue();
            assertThat(none.get("parameters").isEmpty()).isTrue();
        }

        @Test
        @DisplayName("7.3: el administrador ve todas las cartas, también las no disponibles, con su estado de imagen")
        void adminSeesUnavailable() throws Exception {
            JsonNode card = search("code=PKA-PLA-001").get("items").get(0);

            assertThat(card.get("available").asBoolean()).isFalse();
            assertThat(card.get("imageStatus").asString()).isEqualTo("PENDING_RETRY");
            assertThat(card.get("imageSmallUrl").isNull()).isTrue();
            assertThat(codes(search(""))).hasSize(FIXTURE_CARDS);
        }

        @Test
        @DisplayName("C.3 éxito: el tamaño de cada respuesta está acotado (limit por defecto 20, máximo 100) y nextCursor lleva a los demás resultados")
        void boundedResponses() throws Exception {
            JsonNode page = search("");
            assertThat(page.get("limit").asInt()).isEqualTo(20);
            assertThat(page.has("nextCursor")).isTrue();
            assertThat(page.get("nextCursor").isNull()).isTrue();
            assertThat(page.has("total")).isFalse();
            assertThat(page.has("expiresAt")).isFalse();

            assertThat(search("limit=100").get("limit").asInt()).isEqualTo(100);
            assertError(get("limit=101", admin), 400, "INVALID_PAGINATION", "limit");
        }

        @Test
        @DisplayName("7.3: el nombre busca % y _ como texto literal, y las comillas no alteran la consulta")
        void literalWildcards() throws Exception {
            for (String term : List.of("%", "_", "100%_", "'", "\\")) {
                assertThat(codes(search("name=" + encode(term)))).as(term).hasSize(1);
            }
            assertThat(codes(search("name=" + encode("' OR 1=1 --")))).isEmpty();
        }
    }

    // =========================================================================================
    // Criterios de error (C.3)
    // =========================================================================================

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.3 error: sin credenciales responde 401 y sin rol de administrador 403, errores distinguibles, aunque los criterios sean inválidos")
        void authenticationAndAuthorization() throws Exception {
            assertError(get("", null), 401, "NOT_AUTHENTICATED", null);
            assertError(get("", "token-falso"), 401, "NOT_AUTHENTICATED", null);
            assertError(get("", token(Role.PLAYER)), 403, "FORBIDDEN", null);
            // Primero autenticar y autorizar: un criterio inválido no adelanta su error (igual que Crear carta).
            assertError(get("type=FIRE&limit=0", null), 401, "NOT_AUTHENTICATED", null);
            assertError(get("type=FIRE&limit=0", token(Role.PLAYER)), 403, "FORBIDDEN", null);
        }

        @ParameterizedTest(name = "{0}: criterio {1}")
        @CsvSource({ "type=FIRE, type", "rarity=INVALID, rarity", "cost=11, cost", "cost=-1, cost",
                "power=-21, power", "power=21, power", "costMin=5&costMax=1, cost", "powerMin=20&powerMax=0, power",
                "cost=no, cost", "costMin=99, costMin" })
        @DisplayName("C.3 error: un criterio inválido (tipo o rareza inexistente, costo o poder fuera de rango, mínimo mayor que máximo) se rechaza indicando el criterio")
        void invalidCriterion(String query, String criterion) throws Exception {
            assertError(get(query, admin), 400, "INVALID_SEARCH_CRITERIA", criterion);
        }

        @Test
        @DisplayName("C.3 error: un tipo o una rareza inexistentes informan los valores permitidos")
        void allowedValues() throws Exception {
            Result type = get("type=FIRE", admin);
            assertThat(values(type.body().get("allowedValues"))).containsExactly("AGU", "ELE", "FUE", "LUC", "PLA", "PSI");
            Result cost = get("cost=11", admin);
            assertThat(values(cost.body().get("allowedValues"))).containsExactly("0..10");
        }

        @ParameterizedTest(name = "{0}: criterio {1}")
        @CsvSource({ "unknown=1, unknown", "abilityCode=HAB-000, abilityCode", "cost=2&costMin=1, cost",
                "power=1&powerMax=2, power", "type=FUE&type=AGU, type", "name=, name" })
        @DisplayName("7.3: un parámetro desconocido, repetido o vacío, o un valor exacto con un rango del mismo campo, es INVALID_SEARCH_CRITERIA")
        void invalidParameters(String query, String criterion) throws Exception {
            assertError(get(query, admin), 400, "INVALID_SEARCH_CRITERIA", criterion);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "limit=0", "limit=101", "limit=abc", "cursor=malo!", "cursor=MDU", "limit=5&limit=6" })
        @DisplayName("5.5: un limit o un cursor inválidos son INVALID_PAGINATION")
        void invalidPagination(String query) throws Exception {
            assertError(get(query, admin), 400, "INVALID_PAGINATION", query.substring(0, query.indexOf('=')));
        }
    }

    // =========================================================================================
    // Criterios alternos (C.3)
    // =========================================================================================

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.3 alterno: una combinación de criterios sin resultados devuelve una lista vacía, no un error")
        void noResults() throws Exception {
            Result result = get("name=inexistente&type=FUE", admin);

            assertThat(result.status()).isEqualTo(200);
            assertThat(result.body().get("items").isEmpty()).isTrue();
            assertThat(result.body().get("nextCursor").isNull()).isTrue();
        }

        @Test
        @DisplayName("C.3 alterno: una búsqueda sin criterios recorre todo el catálogo con la misma paginación")
        void noCriteriaTraversesAll() throws Exception {
            List<String> seen = traverse("limit=2", null);

            assertThat(seen).containsExactly("PKA-FUE-001", "PKA-AGU-001", "PKA-ELE-001", "PKA-PLA-001", "PKA-PSI-001");
        }

        @Test
        @DisplayName("C.3 alterno: el orden es estable, sin cartas repetidas ni omitidas aunque se creen cartas nuevas durante el recorrido")
        void stableOrderWhileCreating() throws Exception {
            List<String> seen = traverse("limit=2", () -> card("PKA-LUC-001", "Nueva durante el recorrido", 1, 1,
                    "LUC", "COMMON", "HAB-000", true));

            // Las 5 originales, en orden y una sola vez; la nueva tiene un id mayor y aparece al final.
            assertThat(seen).containsExactly("PKA-FUE-001", "PKA-AGU-001", "PKA-ELE-001", "PKA-PLA-001",
                    "PKA-PSI-001", "PKA-LUC-001");
        }

        @Test
        @DisplayName("C.3 alterno: una carta recién creada aparece en los resultados (hoy de inmediato; con la caché, al vencer la página)")
        void newCardAppears() throws Exception {
            assertThat(codes(search("type=LUC"))).isEmpty();

            card("PKA-LUC-001", "Recién creada", 1, 1, "LUC", "COMMON", "HAB-000", true);

            assertThat(codes(search("type=LUC"))).containsExactly("PKA-LUC-001");
        }

        @Test
        @DisplayName("5.5: los criterios se mantienen al pasar de página con el cursor")
        void criteriaWithCursor() throws Exception {
            assertThat(traverse("limit=1&rarity=COMMON", null)).containsExactly("PKA-FUE-001", "PKA-PSI-001");
        }
    }

    // =========================================================================================
    // Ayudantes.
    // =========================================================================================

    private void card(String code, String name, int cost, int power, String type, String rarity, String ability,
            boolean available) {
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, code, name, NameNormalizer.normalize(name), cost, power, type, rarity, ability,
                "/images/cards/" + code + "/large.png", available ? "/images/cards/" + code + "/small.png" : null,
                available ? "READY" : "PENDING_RETRY", available);
    }

    private void resetCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("DELETE FROM ability WHERE code LIKE 'HAB-T-%'");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }

    private String token(Role role) {
        UUID player = TestPlayers.create(accounts).id();
        createdPlayers.add(player);
        return tokenIssuer.issue(new AuthenticatedUser(player, role)).value();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static List<String> codes(JsonNode page) {
        return page.get("items").valueStream().map(item -> item.get("code").asString()).toList();
    }

    private static List<String> values(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }

    /** Búsqueda como administrador que debe responder 200. */
    private JsonNode search(String query) throws Exception {
        Result result = get(query, admin);
        assertThat(result.status()).as(result.raw()).isEqualTo(200);
        return result.body();
    }

    /**
     * Recorre todas las páginas siguiendo nextCursor y devuelve los códigos en orden.
     *
     * @param afterFirstPage acción que se ejecuta después de la primera página (o {@code null})
     */
    private List<String> traverse(String query, Runnable afterFirstPage) throws Exception {
        List<String> seen = new ArrayList<>();
        JsonNode page = search(query);
        seen.addAll(codes(page));
        if (afterFirstPage != null) {
            afterFirstPage.run();
        }
        while (!page.get("nextCursor").isNull()) {
            page = search(query + "&cursor=" + page.get("nextCursor").asString());
            seen.addAll(codes(page));
        }
        return seen;
    }

    private static void assertError(Result result, int status, String type, String field) {
        assertThat(result.status()).as(result.raw()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
        assertThat(result.body().get("correlationId").asString()).isNotBlank();
        if (field != null) {
            assertThat(result.body().get("field").asString()).isEqualTo(field);
        }
    }

    private Result get(String query, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + PATH + (query.isEmpty() ? "" : "?" + query))).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body(), json.readTree(response.body()));
    }

    private record Result(int status, String raw, JsonNode body) {
    }
}
