package cr.ac.tec.pokearena;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import cr.ac.tec.pokearena.application.port.CardSearchQuery;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardSearchException;
import cr.ac.tec.pokearena.infrastructure.persistence.SearchSchemaMigration;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.springframework.test.context.ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
@EnabledIfEnvironmentVariable(named = "POKEARENA_TEST_DATABASE_URL", matches = ".+")
class CardSearchPostgresTests {
    private static final String ADMIN_TOKEN = UUID.randomUUID().toString();
    private static final String USER_TOKEN = UUID.randomUUID().toString();
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired CardSearchQuery query;
    @Autowired SearchSchemaMigration migration;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        // El guard de conexiones comprueba el nombre real antes de inicializar el esquema.
        registry.add("spring.datasource.url", () -> System.getenv("POKEARENA_TEST_DATABASE_URL"));
        registry.add("pokearena.search.test-admin-token", () -> ADMIN_TOKEN);
        registry.add("pokearena.search.test-user-token", () -> USER_TOKEN);
    }

    @BeforeEach
    void fixtures() {
        // Los catálogos TEMP son solo fixtures y no definen habilidades oficiales del juego.
        jdbc.execute("TRUNCATE card_search_snapshots, cards CASCADE");
        jdbc.update("INSERT INTO abilities(code, name, trigger_code, effect_code, parameters) VALUES ('TEST-ABILITY', 'Solo pruebas', 'TEST-REVEAL', 'TEST-BOOST', '{\"amount\":2}'::jsonb) ON CONFLICT(code) DO UPDATE SET trigger_code=EXCLUDED.trigger_code, effect_code=EXCLUDED.effect_code, parameters=EXCLUDED.parameters");
        card("PKA-FUE-001", "Dragón Áureo", 0, -20, "FUE", "COMMON", "HAB-000");
        card("PKA-AGU-001", "Dragón Azul", 5, 10, "AGU", "RARE", "TEST-ABILITY");
        card("PKA-ELE-001", "100%_Volt!", 10, 20, "ELE", "LEGENDARY", "TEST-ABILITY");
        card("PKA-PLA-001", "Bosque", 3, 0, "PLA", "UNCOMMON", "HAB-000");
        card("PKA-PSI-001", "Comilla ' y barra \\ literal", 5, 5, "PSI", "COMMON", "HAB-000");
    }

    private void card(String code, String name, int cost, int power, String type, String rarity, String ability) {
        jdbc.update("INSERT INTO cards(code,name,cost,power,type,rarity,ability_code,large_image_url,small_image_url,image_status,created_at) VALUES(?,?,?,?,?,?,?,'./uploads/large.jpg','./uploads/small.jpg','DISPONIBLE',clock_timestamp())",
                code, name, cost, power, type, rarity, ability);
    }

    private HttpResponse<String> request(String parameters, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/cards" + (parameters.isEmpty() ? "" : "?" + parameters))).timeout(Duration.ofSeconds(10));
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode search(String parameters) throws Exception {
        var response = request(parameters, ADMIN_TOKEN);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return json.readTree(response.body());
    }

    @ParameterizedTest
    @CsvSource({"code,PKA-FUE-001,1", "name,dragon,2", "name,ÁUREO,1", "cost,5,2", "power,0,1",
            "type,AGU,1", "rarity,COMMON,2", "abilityCode,HAB-000,3", "abilityCode,TEST-ABILITY,2",
            "abilityTrigger,TEST-REVEAL,2", "abilityEffect,TEST-BOOST,2", "costMin,5,3", "costMax,3,2",
            "powerMin,10,2", "powerMax,0,2", "cost,0,1", "cost,10,1", "power,-20,1", "power,20,1"})
    void eachFilter(String key, String value, int expected) throws Exception {
        // Cada criterio individual atraviesa HTTP, aplicación y PostgreSQL real.
        assertEquals(expected, search(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)).get("total").asInt());
    }

    @Test
    void combinesCriteriaAndReturnsCompletePayload() throws Exception {
        var page = search("name=DRAGON&costMin=4&costMax=6&powerMin=0&powerMax=15&type=AGU&rarity=RARE&abilityCode=TEST-ABILITY&abilityTrigger=TEST-REVEAL&abilityEffect=TEST-BOOST");
        assertEquals(1, page.get("total").asInt());
        var card = page.get("items").get(0);
        for (String field : List.of("code", "name", "largeImageUrl", "smallImageUrl", "imageStatus", "cost", "power", "type", "rarity", "ability")) {
            assertTrue(card.has(field), field);
        }
        assertEquals(2, card.get("ability").get("parameters").get("amount").asInt());
        var none = search("abilityCode=HAB-000").get("items").get(0).get("ability");
        assertEquals("Sin Habilidad", none.get("name").asString());
        assertTrue(none.get("trigger").isNull());
        assertTrue(none.get("parameters").isEmpty());
        assertEquals(0, search("abilityCode=HAB-000&abilityTrigger=TEST-REVEAL").get("total").asInt());
        assertEquals(0, search("name=missing&type=FUE").get("items").size());
    }

    @Test
    void literalNamesDoNotBecomeWildcardsOrSql() throws Exception {
        // Comodines, escape y comillas se interpretan como texto, no como lenguaje de consulta.
        for (String term : List.of("%", "_", "!", "100%_", "'", "\\")) {
            assertEquals(1, search("name=" + URLEncoder.encode(term, StandardCharsets.UTF_8)).get("total").asInt(), term);
        }
        assertEquals(0, search("name=" + URLEncoder.encode("' OR 1=1 --", StandardCharsets.UTF_8)).get("total").asInt());
    }

    @ParameterizedTest
    @CsvSource({"type=FIRE,type", "rarity=INVALID,rarity", "cost=11,cost", "power=-21,power",
            "costMin=5&costMax=1,cost", "powerMin=20&powerMax=0,power", "cost=2&costMin=1,cost",
            "power=1&powerMax=2,power", "cost=no,cost", "pageSize=101,pageSize", "pageSize=0,pageSize",
            "cursor=bad,cursor", "type=FUE&type=AGU,type", "name=,name", "unknown=1,unknown"})
    void errorsIdentifyTheCriterion(String parameters, String criterion) throws Exception {
        var response = request(parameters, ADMIN_TOKEN);
        assertEquals(400, response.statusCode(), response.body());
        assertEquals(criterion, json.readTree(response.body()).get("criterion").asString());
    }

    @Test
    void authenticationAndAuthorizationAreDistinct() throws Exception {
        // Una cabecera de rol libre no sustituye una credencial configurada.
        assertEquals(401, request("", null).statusCode());
        assertEquals(401, request("", "untrusted").statusCode());
        assertEquals(403, request("", USER_TOKEN).statusCode());
        var forged = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/cards"))
                .header("X-Role", "ADMIN").GET().build();
        assertEquals(401, http.send(forged, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void traversesSnapshotDespiteEarlyUncommittedAndLaterInserts() throws Exception {
        // La transacción empieza y escribe antes del snapshot, pero confirma entre páginas.
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO cards SELECT 'PKA-AAA-LATE',name,cost,power,type,rarity,ability_code,large_image_url,small_image_url,image_status,NULL,created_at FROM cards LIMIT 1")) {
                insert.executeUpdate();
            }
            var first = search("pageSize=2");
            assertEquals(5, first.get("total").asInt());
            String next = first.get("nextCursor").asString();
            connection.commit();
            card("PKA-ZZZ-LATE", "Nueva", 1, 1, "FUE", "COMMON", "HAB-000");
            jdbc.update("UPDATE abilities SET parameters = '{\"amount\":99}' WHERE code='TEST-ABILITY'");
            var seen = new HashSet<String>();
            first.get("items").forEach(item -> assertTrue(seen.add(item.get("code").asString())));
            while (next != null) {
                var page = search("pageSize=2&cursor=" + next);
                page.get("items").forEach(item -> {
                    assertTrue(seen.add(item.get("code").asString()));
                    if ("TEST-ABILITY".equals(item.get("ability").get("code").asString())) {
                        assertEquals(2, item.get("ability").get("parameters").get("amount").asInt());
                    }
                });
                next = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
            }
            assertEquals(5, seen.size());
            assertFalse(seen.contains("PKA-AAA-LATE"));
            assertFalse(seen.contains("PKA-ZZZ-LATE"));
            assertEquals(7, search("").get("total").asInt());
        }
    }

    @Test
    void bindsCursorToFiltersSizeOwnerAndExpiration() throws Exception {
        var first = search("pageSize=1&name=dragon");
        String cursor = first.get("nextCursor").asString();
        assertEquals(200, request("pageSize=1&name=dragon&cursor=" + cursor, ADMIN_TOKEN).statusCode());
        assertEquals(400, request("pageSize=1&name=other&cursor=" + cursor, ADMIN_TOKEN).statusCode());
        assertEquals(400, request("pageSize=2&name=dragon&cursor=" + cursor, ADMIN_TOKEN).statusCode());
        var error = assertThrows(CardSearchException.class, () -> query.search(new CardSearchCriteria(Map.of("name", "dragon")), 1, UUID.fromString(cursor), "other-admin"));
        assertEquals(CardSearchException.Reason.FORBIDDEN, error.reason());
        jdbc.update("UPDATE card_search_snapshots SET expires_at = clock_timestamp() - INTERVAL '1 second'");
        assertEquals(410, request("pageSize=1&name=dragon&cursor=" + cursor, ADMIN_TOKEN).statusCode());
        assertEquals(410, request("cursor=" + UUID.randomUUID(), ADMIN_TOKEN).statusCode());
    }

    @Test
    void repeatedCursorDoesNotGrowStateAndCleanupCascades() throws Exception {
        var first = search("pageSize=1");
        String cursor = first.get("nextCursor").asString();
        String next = search("pageSize=1&cursor=" + cursor).get("nextCursor").asString();
        assertEquals(next, search("pageSize=1&cursor=" + cursor).get("nextCursor").asString());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM card_search_cursors", Integer.class));
        jdbc.update("UPDATE card_search_snapshots SET expires_at = clock_timestamp() - INTERVAL '1 second'");
        ((cr.ac.tec.pokearena.infrastructure.persistence.JdbcCardSearchQuery) query).removeExpiredSnapshots();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_results", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_cursors", Integer.class));
    }

    @Test
    void completedInitialSearchesNeverReserveState() throws Exception {
        // Más de veinte respuestas vacías y completas no deben consumir capacidad ni dejar filas.
        for (int i = 0; i < 25; i++) {
            assertEquals(0, search("name=not-present").get("total").asInt());
            assertEquals(5, search("").get("total").asInt());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_results", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_cursors", Integer.class));
        }
    }

    @Test
    void completedSearchesWorkAtTraversalCapacityAndLastPageRemainsRetryable() throws Exception {
        // Ocupa veinte recorridos reales, sin impedir respuestas que no necesitan cursor.
        String cursor = null;
        for (int i = 0; i < 20; i++) { cursor = search("pageSize=2").get("nextCursor").asString(); }
        int rows = jdbc.queryForObject("SELECT count(*) FROM card_search_results", Integer.class);
        assertEquals(5, search("").get("total").asInt());
        assertEquals(0, search("name=not-present").get("total").asInt());
        assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
        assertEquals(rows, jdbc.queryForObject("SELECT count(*) FROM card_search_results", Integer.class));
        assertEquals(503, request("pageSize=2", ADMIN_TOKEN).statusCode());
        var second = search("pageSize=2&cursor=" + cursor);
        String lastCursor = second.get("nextCursor").asString();
        var last = search("pageSize=2&cursor=" + lastCursor);
        assertTrue(last.get("nextCursor").isNull());
        assertEquals(last, search("pageSize=2&cursor=" + lastCursor));
        assertEquals(second, search("pageSize=2&cursor=" + cursor));
        assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
    }

    @Test
    void repeatedParametersAreCheckedAfterAuthenticationAndPermissions() throws Exception {
        // El mismo parámetro repetido distingue identidad, permisos y error de criterio.
        assertEquals(401, request("type=FUE&type=AGU", null).statusCode());
        assertEquals(401, request("type=FUE&type=AGU", "invalid").statusCode());
        assertEquals(403, request("type=FUE&type=AGU", USER_TOKEN).statusCode());
        var response = request("type=FUE&type=AGU", ADMIN_TOKEN);
        assertEquals(400, response.statusCode());
        assertEquals("type", json.readTree(response.body()).get("criterion").asString());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
    }

    @Test
    void limitsCapacityAndRollsBackOversizedSnapshots() throws Exception {
        // Nunca devuelve un catálogo truncado ni retiene snapshots fallidos.
        jdbc.update("INSERT INTO card_search_snapshots(id,owner_id,filters,page_size) SELECT gen_random_uuid(),'test','',50 FROM generate_series(1,20)");
        assertEquals(503, request("pageSize=1", ADMIN_TOKEN).statusCode());
        jdbc.execute("TRUNCATE card_search_snapshots CASCADE");
        jdbc.update("UPDATE cards SET large_image_url = repeat('x',17000) WHERE code='PKA-FUE-001'");
        assertEquals(503, request("pageSize=1", ADMIN_TOKEN).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
    }

    @Test
    void rejectsRowAndByteCapacityWithoutKeepingPartialResults() throws Exception {
        // Los límites globales se evalúan dentro de la misma transacción de admisión.
        jdbc.update("INSERT INTO card_search_snapshots(id,owner_id,filters,page_size,payload_bytes) VALUES (?, 'other', '', 50, 67108864)", UUID.randomUUID());
        assertEquals(503, request("pageSize=1", ADMIN_TOKEN).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
        jdbc.execute("TRUNCATE card_search_snapshots CASCADE");
        jdbc.update("INSERT INTO cards(code,name,cost,power,type,rarity,ability_code,large_image_url,small_image_url,image_status,created_at) SELECT 'LIMIT-'||i, 'Capacidad',0,0,'FUE','COMMON','HAB-000','large','small','DISPONIBLE',clock_timestamp() FROM generate_series(1,20001) i");
        assertEquals(503, request("pageSize=1", ADMIN_TOKEN).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM card_search_results", Integer.class));
    }

    @Test
    void admissionContentionDoesNotBlockAnExistingTraversal() throws Exception {
        // Otro proceso puede retener el bloqueo; continuar una búsqueda sigue siendo independiente.
        String cursor = search("pageSize=1").get("nextCursor").asString();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(390039)");
                assertEquals(503, request("pageSize=1", ADMIN_TOKEN).statusCode());
                assertEquals(200, request("pageSize=1&cursor=" + cursor, ADMIN_TOKEN).statusCode());
            }
            connection.rollback();
        }
    }

    @Test
    void legacyAbilityMetadataIsNotInvented() throws Exception {
        // Una habilidad creada con el contrato anterior sigue siendo consultable sin metadatos ficticios.
        jdbc.update("INSERT INTO abilities(code,name) VALUES ('TEST-LEGACY-METADATA','Anterior') ON CONFLICT DO NOTHING");
        card("PKA-LUC-001", "Legada", 1, 1, "LUC", "COMMON", "TEST-LEGACY-METADATA");
        var ability = search("code=PKA-LUC-001").get("items").get(0).get("ability");
        assertEquals("Anterior", ability.get("name").asString());
        assertTrue(ability.get("trigger").isNull());
        assertTrue(ability.get("effect").isNull());
        assertTrue(ability.get("parameters").isEmpty());
        assertEquals("Habilidad Base", jdbc.queryForObject("SELECT name FROM abilities WHERE code='HAB-000'", String.class));
    }

    @Test
    void versionedMigrationCanRunAgainWithoutChangingExistingData() throws Exception {
        migration.afterPropertiesSet();
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM cards", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM search_schema_versions WHERE version=39", Integer.class));
    }

    @Test
    void tenThousandCardsRemainBoundedAndTraversable() throws Exception {
        // La semilla masiva vive solo en la base desechable de pruebas.
        jdbc.update("INSERT INTO cards(code,name,cost,power,type,rarity,ability_code,large_image_url,small_image_url,image_status,created_at) SELECT 'LOAD-'||lpad(i::text,5,'0'), 'Load '||lpad(i::text,5,'0'), i%11, i%41-20, 'FUE','COMMON','HAB-000','large','small','DISPONIBLE',clock_timestamp() FROM generate_series(1,10000) i");
        jdbc.execute("ANALYZE cards");
        String plan = String.join("\n", jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) SELECT code FROM cards WHERE public.us39_normalize(name) LIKE '%09999%'", String.class));
        assertTrue(plan.contains("us39_cards_name"), plan);
        var measurements = new ArrayList<Long>();
        for (int i = 0; i < 15; i++) {
            jdbc.execute("TRUNCATE card_search_snapshots CASCADE");
            long start = System.nanoTime();
            assertEquals(10005, search("pageSize=100").get("total").asInt());
            if (i >= 3) { measurements.add((System.nanoTime() - start) / 1000000); }
        }
        measurements.sort(Long::compareTo);
        long p95 = measurements.get((int) Math.ceil(measurements.size() * 0.95) - 1);
        System.out.println("US39 initial search p95 (10005 cards): " + p95 + " ms");
        assertTrue(p95 < 1000, "Objetivo local p95 < 1000 ms; observado " + p95);
        jdbc.execute("TRUNCATE card_search_snapshots CASCADE");
        assertEquals(50, search("").get("items").size());
        var page = search("pageSize=100");
        var continuationTimes = new ArrayList<Long>();
        var seen = new HashSet<String>();
        do {
            assertTrue(page.get("items").size() <= 100);
            assertTrue(json.writeValueAsBytes(page).length < 1650000);
            page.get("items").forEach(item -> assertTrue(seen.add(item.get("code").asString())));
            if (page.get("nextCursor").isNull()) { break; }
            long start = System.nanoTime();
            page = search("pageSize=100&cursor=" + page.get("nextCursor").asString());
            continuationTimes.add((System.nanoTime() - start) / 1000000);
        } while (true);
        assertEquals(10005, seen.size());
        continuationTimes.sort(Long::compareTo);
        long continuationP95 = continuationTimes.get((int) Math.ceil(continuationTimes.size() * 0.95) - 1);
        assertTrue(continuationP95 < 1000, "Continuación p95: " + continuationP95);
        System.out.println("US39 continuation p95: " + continuationP95 + " ms");
        System.out.println("US39 trigram plan: " + plan);
    }
}
