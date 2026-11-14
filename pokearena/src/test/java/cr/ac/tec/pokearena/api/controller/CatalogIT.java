package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.infrastructure.persistence.CardSearchSql;
import cr.ac.tec.pokearena.support.OpenApiContract;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de la historia 3.1.2 "Explorar el catálogo y mi colección"
 * (criterios C.6).
 * Responsabilidades: verificar {@code GET /api/v1/catalogo} con la aplicación completa, credenciales
 * propias reales y PostgreSQL real: éxito (campos, filtros, propiedad, copias, paginación), cada error con
 * su status y su type, los alternos, el contrato de la respuesta y el rendimiento con 10.000 cartas.
 * Referencia: sección 8.3, Anexo A.6 y Anexo C.6.
 *
 * <p>Misma estructura que las demás historias: clases {@code @Nested} Éxito, Errores y Alternos, con un
 * {@code @DisplayName} que cita el criterio. Cada prueba parte de un catálogo conocido (cartas visibles y
 * excluidas, con dueño) y lo borra al terminar, en orden de claves foráneas; las pruebas corren en
 * secuencia (sección 13).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Historia 3.1.2: explorar el catálogo y mi colección (API completa)")
class CatalogIT extends PostgresIntegrationTestBase {

    private static final String PATH = "/api/v1/catalogo";
    private static final List<String> VISIBLE = List.of("PKA-FUE-001", "PKA-AGU-001", "PKA-FUE-002", "PKA-PLA-001");
    // Objetivo y mediciones de rendimiento de la sección 13, después de calentar la JVM.
    private static final int LARGE_CATALOG_SIZE = 10_000;
    private static final int WARM_UP_REQUESTS = 10;
    private static final int MEASURED_REQUESTS = 20;
    private static final int P95_INDEX = 18;
    private static final long MAX_PAGE_MILLIS = 300;
    private static final long NANOS_PER_MILLI = 1_000_000;
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

    private UUID first;
    private UUID second;
    private String firstToken;
    private String secondToken;

    @BeforeEach
    void prepare() {
        // Un catálogo pequeño con extremos de costo y cartas excluidas que también tienen dueño.
        jdbc.update("DELETE FROM card");
        card("PKA-FUE-001", "FUE", 0, true, true, "READY");
        card("PKA-AGU-001", "AGU", 3, true, true, "READY");
        card("PKA-FUE-002", "FUE", 7, true, true, "READY");
        card("PKA-PLA-001", "PLA", 10, true, true, "READY");
        card("PKA-ELE-001", "ELE", 3, true, false, "READY");
        card("PKA-PSI-001", "PSI", 3, false, true, "PENDING_RETRY");
        card("PKA-LUC-001", "LUC", 3, false, true, "FAILED");
        card("PKA-AGU-002", "AGU", 3, false, true, "READY");
        first = player();
        second = player();
        firstToken = token(first, Role.PLAYER);
        secondToken = token(second, Role.PLAYER);
        own(first, "PKA-FUE-001", 3);
        own(first, "PKA-FUE-002", 1);
        own(first, "PKA-ELE-001", 2);
        own(first, "PKA-PSI-001", 2);
        own(first, "PKA-LUC-001", 2);
        own(first, "PKA-AGU-002", 2);
        own(second, "PKA-AGU-001", 5);
    }

    @AfterEach
    void cleanUp() {
        // Primero las colecciones: ninguna prueba deja referencias que impidan limpiar el catálogo.
        TestPlayers.delete(jdbc, players);
        jdbc.update("DELETE FROM card");
    }

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.6 éxito: el catálogo muestra las cartas de forma paginada con nombre, imagen, costo, poder, tipo, rareza y habilidad")
        void ownershipAndFields() throws Exception {
            JsonNode page = page("", firstToken);
            assertThat(codes(page)).containsExactlyElementsOf(VISIBLE);
            assertThat(page.get("limit").asInt()).isEqualTo(20);
            assertThat(page.get("nextCursor").isNull()).isTrue();
            JsonNode own = page.get("items").get(0);
            assertThat(own.get("owned").asBoolean()).isTrue();
            assertThat(own.get("copies").asInt()).isEqualTo(3);
            assertThat(own.get("name").asString()).isEqualTo("Carta PKA-FUE-001");
            assertThat(own.get("cost").asInt()).isZero();
            assertThat(own.get("power").asInt()).isEqualTo(-2);
            assertThat(own.get("type").get("code").asString()).isEqualTo("FUE");
            assertThat(own.get("rarity").get("code").asString()).isEqualTo("COMMON");
            assertThat(own.get("ability").get("code").asString()).isEqualTo("HAB-000");
            assertThat(own.get("ability").get("name").asString()).isNotBlank();
            assertThat(own.get("imageLargeUrl").asString()).endsWith("/large.png");
            assertThat(own.get("imageSmallUrl").asString()).endsWith("/small.png");
            assertThat(page.get("items").get(1).get("owned").asBoolean()).isFalse();
            assertThat(page.get("items").get(1).get("copies").asInt()).isZero();
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "type=FUE | PKA-FUE-001,PKA-FUE-002",
                "costMin=7 | PKA-FUE-002,PKA-PLA-001",
                "costMax=3 | PKA-FUE-001,PKA-AGU-001",
                "costMin=3&costMax=7 | PKA-AGU-001,PKA-FUE-002",
                "type=FUE&costMin=1&costMax=7 | PKA-FUE-002",
                "owned=true&type=FUE&costMin=1&costMax=7 | PKA-FUE-002",
                "costMin=0&costMax=0 | PKA-FUE-001",
                "costMin=10&costMax=10 | PKA-PLA-001" })
        @DisplayName("C.6 éxito: los filtros por tipo y por costo pueden combinarse y los resultados cumplen todos los filtros activos (extremos inclusivos)")
        void filters(String query, String expected) throws Exception {
            assertThat(codes(page(query, firstToken))).containsExactly(expected.split(","));
        }

        @Test
        @DisplayName("C.6 éxito: cada carta indica si el jugador la posee y cuántas copias tiene, solo de su propia colección; un filtro muestra solo las propias")
        void isolatesPlayers() throws Exception {
            JsonNode a = page("", firstToken);
            JsonNode b = page("", secondToken);
            assertThat(codes(a)).isEqualTo(codes(b));
            assertThat(b.get("items").get(0).get("copies").asInt()).isZero();
            assertThat(b.get("items").get(1).get("copies").asInt()).isEqualTo(5);
            assertThat(codes(page("owned=true", firstToken))).containsExactly("PKA-FUE-001", "PKA-FUE-002");
            JsonNode secondOwned = page("owned=true", secondToken);
            assertThat(codes(secondOwned)).containsExactly("PKA-AGU-001");
            assertThat(secondOwned.get("items").get(0).get("copies").asInt()).isEqualTo(5);
            assertThat(secondOwned.get("items").get(0).get("owned").asBoolean()).isTrue();
            // Un id enviado por el cliente no selecciona la colección de otro jugador.
            error(get(PATH, "playerId=" + second, firstToken), 400, "INVALID_FIELD", "playerId");
        }

        @Test
        @DisplayName("C.6 éxito (paginado) y 5.5: páginas sin duplicados ni omisiones, también con owned y filtros")
        void pagination() throws Exception {
            assertThat(traverse("limit=1", firstToken)).containsExactlyElementsOf(VISIBLE);
            assertThat(traverse("limit=1&owned=true&type=FUE", firstToken))
                    .containsExactly("PKA-FUE-001", "PKA-FUE-002");
            assertThat(traverse("limit=1&costMin=3", firstToken))
                    .containsExactly("PKA-AGU-001", "PKA-FUE-002", "PKA-PLA-001");
        }

        @Test
        @DisplayName("C.6 éxito y 8.3: solo cartas disponibles y activas; la búsqueda del administrador sigue viendo todas")
        void visibilityAndAdmin() throws Exception {
            String admin = token(first, Role.ADMIN);
            assertThat(codes(page("", admin))).containsExactlyElementsOf(VISIBLE);
            assertThat(codes(page("owned=true", admin))).containsExactly("PKA-FUE-001", "PKA-FUE-002");
            Result administrative = get("/api/v1/cartas", "", admin);
            assertThat(administrative.status()).isEqualTo(200);
            assertThat(codes(administrative.body())).hasSize(8)
                    .contains("PKA-ELE-001", "PKA-PSI-001", "PKA-LUC-001", "PKA-AGU-002");
        }
    }

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.6 errores y 5.2: sin sesión o con token inválido se rechaza con 401 antes de validar filtros")
        void authentication() throws Exception {
            for (String bearer : new String[] {null, "invalid", tokens.issue(new AuthenticatedUser(first, Role.PLAYER))
                    .value() + "alterado"}) {
                error(get(PATH, "type=INVALID&limit=0", bearer), 401, "NOT_AUTHENTICATED", null);
            }
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "type=INVALID | type", "type=fue | type", "costMin=-1 | costMin", "costMax=11 | costMax",
                "costMin=abc | costMin", "costMax=1.5 | costMax", "costMin=8&costMax=2 | costMin",
                "owned=yes | owned", "owned=TRUE | owned", "type= | type", "owned= | owned",
                "type=FUE&type=AGU | type", "costMin=1&costMin=2 | costMin", "cost=3 | cost" })
        @DisplayName("C.6 errores: un filtro con un valor inválido (tipo inexistente o costo fuera de rango) es rechazado indicando los valores permitidos")
        void invalidFilters(String query, String field) throws Exception {
            Result response = get(PATH, query, firstToken);
            error(response, 400, "INVALID_FIELD", field);
            assertThat(response.body().get("allowedValues").size()).isPositive();
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "limit=0 | limit", "limit=101 | limit", "limit=-1 | limit", "limit=abc | limit",
                "limit=1.5 | limit", "limit= | limit", "limit=1&limit=2 | limit",
                "cursor= | cursor", "cursor=invalid | cursor", "cursor=MQ== | cursor",
                "cursor=MDE | cursor", "cursor=KzE | cursor", "cursor=MQ&cursor=Mg | cursor" })
        @DisplayName("C.6 errores: un parámetro de paginación inválido devuelve un error descriptivo (INVALID_PAGINATION), no un error del servidor")
        void invalidPagination(String query, String field) throws Exception {
            error(get(PATH, query, firstToken), 400, "INVALID_PAGINATION", field);
        }
    }

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.6 alternos: una combinación de filtros sin resultados (también una colección vacía) devuelve una lista vacía, no un error")
        void emptyAndOwnedFalse() throws Exception {
            assertThat(codes(page("type=AGU&costMin=9", firstToken))).isEmpty();
            assertThat(codes(page("owned=true&type=PLA", firstToken))).isEmpty();
            String emptyPlayer = token(player(), Role.PLAYER);
            JsonNode empty = page("owned=true", emptyPlayer);
            assertThat(codes(empty)).isEmpty();
            assertThat(empty.get("nextCursor").isNull()).isTrue();
            assertThat(codes(page("owned=false", emptyPlayer))).containsExactlyElementsOf(VISIBLE);
            assertThat(page("", emptyPlayer).get("items").valueStream()
                    .allMatch(item -> !item.get("owned").asBoolean() && item.get("copies").asInt() == 0)).isTrue();
        }

        @Test
        @DisplayName("C.6 alternos: una carta recién creada aparece en el catálogo (aquí sin caché, de inmediato)")
        void newlyCreatedCard() throws Exception {
            assertThat(codes(page("type=LUC", firstToken))).isEmpty();
            card("PKA-LUC-002", "LUC", 1, true, true, "READY");
            assertThat(codes(page("type=LUC", firstToken))).containsExactly("PKA-LUC-002");
        }

        @Test
        @DisplayName("C.6 alternos: el catálogo y la colección muestran la versión grande de la imagen de cada carta")
        void largeImageInCatalogAndCollection() throws Exception {
            for (String query : new String[] {"", "owned=true"}) {
                assertThat(page(query, firstToken).get("items").valueStream()
                        .map(item -> item.get("imageLargeUrl").asString()))
                        .as(query).isNotEmpty().allMatch(url -> url.endsWith("/large.png"));
            }
        }
    }

    @Nested
    @DisplayName("Contrato y rendimiento")
    class ContractAndPerformance {

        @Test
        @DisplayName("10: la respuesta real coincide con las propiedades requeridas del catálogo en el contrato")
        @SuppressWarnings("unchecked")
        void responseContract() throws Exception {
            // Lee el contrato publicado: no duplica una segunda lista manual de campos del endpoint.
            try (var input = getClass().getResourceAsStream(OpenApiContract.RESOURCE)) {
                Map<String, Object> contract = new Yaml().load(input);
                Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) contract.get("components")).get("schemas");
                JsonNode response = page("limit=1", firstToken);
                // Conserva una respuesta real de HTTP para la revisión manual de la entrega.
                Files.writeString(Path.of("target/catalog-response-example.json"),
                        response.toPrettyString());
                assertShape(response, (Map<String, Object>) schemas.get("CatalogPage"));
                assertShape(response.get("items").get(0), (Map<String, Object>) schemas.get("CatalogCard"));
            }
        }

        @Test
        @DisplayName("13 y C.6: catálogo de 10.000 cartas con páginas menores a 300 ms e índices, también owned=true")
        void largeCatalogPerformance() throws Exception {
            // Usa la semilla oficial, eliminando primero las referencias de las colecciones de la prueba.
            jdbc.update("DELETE FROM player_card WHERE player_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM card");
            jdbc.execute(Files.readString(Path.of("scripts/seed-10000-cartas.sql")));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM card", Integer.class)).isEqualTo(LARGE_CATALOG_SIZE);
            jdbc.update("INSERT INTO player_card (player_id, card_code, copies) SELECT ?, code, 2 FROM card WHERE id % 4 = 0", first);
            jdbc.execute("ANALYZE player_card");

            // Las mediciones son por HTTP y usan la misma caché que el resto de las pruebas.
            List<String> measurements = new ArrayList<>();
            for (String query : List.of("limit=100", "type=FUE&costMin=3&costMax=5", "owned=true&limit=100")) {
                for (int i = 0; i < WARM_UP_REQUESTS; i++) {
                    page(query, firstToken);
                }
                List<Long> millis = new ArrayList<>();
                for (int i = 0; i < MEASURED_REQUESTS; i++) {
                    long start = System.nanoTime();
                    Result result = get(PATH, query, firstToken);
                    millis.add((System.nanoTime() - start) / NANOS_PER_MILLI);
                    assertThat(result.status()).isEqualTo(200);
                }
                millis.sort(Long::compareTo);
                long p95 = millis.get(P95_INDEX);
                measurements.add(query + ": p95=" + p95 + " ms");
                assertThat(p95).as(query).isLessThan(MAX_PAGE_MILLIS);
            }

            // EXPLAIN usa el mismo generador que JDBC, sin forzar índices ni cambiar el planificador.
            for (UUID owner : new UUID[] {null, first}) {
                CardSearchSql.Query query = CardSearchSql.catalog(CardSearchCriteria.builder().build(), 0, 101, owner);
                String plan = String.join("\n", jdbc.queryForList("EXPLAIN " + query.sql(), String.class, query.arguments().toArray()));
                assertThat(plan).contains("Index").doesNotContain("Seq Scan on card ");
                measurements.add(plan);
            }
            Files.writeString(Path.of("target/catalog-performance.txt"), String.join("\n", measurements));
        }
    }

    /** Verifica nombres reales contra required y properties del esquema, sin aceptar campos inventados. */
    @SuppressWarnings("unchecked")
    private static void assertShape(JsonNode value, Map<String, Object> schema) {
        List<String> actual = new ArrayList<>();
        value.propertyNames().forEach(actual::add);
        assertThat(actual).containsAll((List<String>) schema.get("required"));
        assertThat(((Map<String, Object>) schema.get("properties")).keySet()).containsAll(actual);
    }

    /** Inserta datos conocidos: aquí se prueba consultar, no la creación de cartas ni imágenes. */
    private void card(String code, String type, int cost, boolean available, boolean active, String imageStatus) {
        jdbc.update("""
                INSERT INTO card (code, name, name_normalized, cost, power, type_code, rarity_code, ability_code,
                                  image_large_url, image_small_url, image_status, available, active, created_at)
                VALUES (?, ?, ?, ?, -2, ?, 'COMMON', 'HAB-000', ?, ?, ?, ?, ?, now())
                """, code, "Carta " + code, "carta " + code, cost, type,
                "/images/cards/" + code + "/large.png", "READY".equals(imageStatus) ? "/images/cards/" + code + "/small.png" : null,
                imageStatus, available, active);
    }

    /** Colecciones diferentes y varias copias permiten detectar fugas y errores de conteo. */
    private void own(UUID player, String code, int copies) {
        jdbc.update("INSERT INTO player_card (player_id, card_code, copies) VALUES (?, ?, ?)", player, code, copies);
    }

    private UUID player() {
        UUID id = TestPlayers.create(accounts).id();
        players.add(id);
        return id;
    }

    private String token(UUID player, Role role) {
        return tokens.issue(new AuthenticatedUser(player, role)).value();
    }

    private JsonNode page(String query, String bearer) throws Exception {
        Result result = get(PATH, query, bearer);
        assertThat(result.status()).as(result.body().toString()).isEqualTo(200);
        return result.body();
    }

    /** Sigue únicamente cursores devueltos por el servidor, con un límite para detectar bucles. */
    private List<String> traverse(String query, String bearer) throws Exception {
        List<String> seen = new ArrayList<>();
        JsonNode page = page(query, bearer);
        while (true) {
            seen.addAll(codes(page));
            assertThat(seen.size()).isLessThanOrEqualTo(VISIBLE.size());
            if (page.get("nextCursor").isNull()) {
                return seen;
            }
            page = page(query + "&cursor=" + page.get("nextCursor").asString(), bearer);
        }
    }

    private static List<String> codes(JsonNode page) {
        return page.get("items").valueStream().map(item -> item.get("code").asString()).toList();
    }

    /** Comprueba el formato único, incluido el identificador que permite rastrear la solicitud. */
    private static void error(Result response, int status, String type, String field) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(status);
        assertThat(response.body().get("type").asString()).isEqualTo(type);
        assertThat(response.body().get("status").asInt()).isEqualTo(status);
        assertThat(response.body().get("correlationId").asString()).isNotBlank();
        if (field != null) {
            assertThat(response.body().get("field").asString()).isEqualTo(field);
        }
    }

    private Result get(String path, String query, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path
                + (query.isEmpty() ? "" : "?" + query))).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), json.readTree(response.body()));
    }

    private record Result(int status, JsonNode body) {
    }
}
