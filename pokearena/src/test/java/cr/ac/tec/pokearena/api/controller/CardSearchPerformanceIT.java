package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.infrastructure.persistence.CardSearchSql;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: prueba de rendimiento de la búsqueda con 10.000 cartas (secciones 13 y 14; criterio C.3).
 * Responsabilidades: cargar {@code scripts/seed-10000-cartas.sql}, medir que una página responde en menos
 * de 300 ms, comprobar con {@code EXPLAIN} que la consulta usa índices y recorrer todo el catálogo con
 * la paginación sin repetidos ni omitidos. Imprime las medidas para documentarlas.
 * Referencia: sección 13 (fila "Rendimiento"), sección 14 y criterio C.3 ("el tiempo de respuesta y el
 * tamaño de cada respuesta se mantienen acotados ... y el administrador puede llegar a cualquiera").
 *
 * <p>Decisiones:
 * <ul>
 *   <li>El script se carga una sola vez para toda la clase ({@code PER_CLASS}) y se borra al final.</li>
 *   <li>Se mide por HTTP, de punta a punta (interceptor, caso de uso, JDBC y JSON), después de unas
 *       solicitudes de calentamiento (la JVM compila el código caliente). Se toma el percentil 95.</li>
 *   <li>El plan se pide para la MISMA consulta que arma la aplicación ({@link CardSearchSql}), y se
 *       comprueba que la tabla card no se lee completa ("Seq Scan on card").</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Buscar carta con 10.000 cartas (rendimiento)")
class CardSearchPerformanceIT extends PostgresIntegrationTestBase {

    private static final Path SEED_SCRIPT = Path.of("scripts", "seed-10000-cartas.sql");
    private static final int SEEDED_CARDS = 10_000;
    /** Objetivo de la sección 13: una página en menos de 300 ms. */
    private static final long MAX_PAGE_MILLIS = 300;
    private static final int WARM_UP_REQUESTS = 10;
    private static final int MEASURED_REQUESTS = 20;
    private static final double PERCENTILE = 0.95;
    private static final long NANOS_PER_MILLI = 1_000_000;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Value("${app.pagination.max-limit}")
    private int maxLimit;

    private UUID adminPlayer;
    private String admin;

    @BeforeAll
    void seed() throws IOException {
        clearCatalog();
        jdbc.execute(Files.readString(SEED_SCRIPT));
        adminPlayer = TestPlayers.create(accounts).id();
        admin = tokenIssuer.issue(new AuthenticatedUser(adminPlayer, Role.ADMIN)).value();
    }

    @AfterAll
    void clean() {
        clearCatalog();
        TestPlayers.delete(jdbc, List.of(adminPlayer));
    }

    @Test
    @DisplayName("14: el script carga 10.000 cartas válidas, con Comunes de sobra, y deja el consecutivo de cada tipo en su último número")
    void seedIsValid() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card", Integer.class)).isEqualTo(SEEDED_CARDS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card WHERE rarity_code = 'COMMON'", Integer.class))
                .isGreaterThanOrEqualTo(SEEDED_CARDS / 2);
        // Códigos con más de 3 dígitos sin cortar (lpad corta: '1000' daría '100').
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card WHERE code = 'PKA-AGU-1000'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT code) FROM card", Integer.class)).isEqualTo(SEEDED_CARDS);
        Integer mismatched = jdbc.queryForObject("""
                SELECT count(*) FROM card_type_counter k
                WHERE k.last_number <> (SELECT max(substring(c.code FROM '[0-9]+$')::int)
                                        FROM card c WHERE c.type_code = k.type_code)
                """, Integer.class);
        assertThat(mismatched).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = { "sin criterios |", "nombre parcial | name=prueba%20fue%201234",
            "tipo y rango de costo | type=FUE&costMin=3&costMax=5", "rareza y poder | rarity=COMMON&powerMin=0",
            "límite máximo | limit=100", "código exacto | code=PKA-PSI-042" })
    @DisplayName("13 y C.3 éxito: con 10.000 cartas, una página responde en menos de 300 ms")
    void pageIsFast(String description, String query) throws Exception {
        String path = query == null ? "" : query;
        for (int i = 0; i < WARM_UP_REQUESTS; i++) {
            assertThat(get(path).statusCode()).isEqualTo(200);
        }
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < MEASURED_REQUESTS; i++) {
            long start = System.nanoTime();
            HttpResponse<String> response = get(path);
            millis.add((System.nanoTime() - start) / NANOS_PER_MILLI);
            assertThat(response.statusCode()).isEqualTo(200);
        }
        long p95 = percentile(millis);
        System.out.printf("Buscar carta, 10.000 cartas, %s: p95 = %d ms (medidas: %s)%n", description, p95, millis);
        assertThat(p95).as("p95 de '%s'", description).isLessThan(MAX_PAGE_MILLIS);
    }

    @Test
    @DisplayName("13: el plan de la consulta usa índices (EXPLAIN) y nunca lee la tabla card completa")
    void planUsesIndexes() {
        long lastId = jdbc.queryForObject("SELECT max(id) FROM card", Long.class);

        String firstPage = plan(CardSearchCriteria.builder().build(), 0);
        String deepPage = plan(CardSearchCriteria.builder().build(), lastId - maxLimit);
        String byName = plan(CardSearchCriteria.builder().nameFragment("prueba fue 1234").build(), 0);
        String byCode = plan(CardSearchCriteria.builder().code("PKA-PSI-042").build(), 0);
        String byTypeAndCost = plan(CardSearchCriteria.builder().type("FUE").costBetween(3, 5).build(), 0);

        assertThat(firstPage).contains("pk_card");
        assertThat(deepPage).contains("pk_card");
        assertThat(byName).contains("idx_card_name_normalized_trgm");
        assertThat(byCode).contains("uq_card_code");
        for (String plan : List.of(firstPage, deepPage, byName, byCode, byTypeAndCost)) {
            assertThat(plan).doesNotContain("Seq Scan on card ");
        }
        System.out.println("Plan de la búsqueda por nombre:\n" + byName);
        System.out.println("Plan de la página sin criterios:\n" + firstPage);
    }

    @Test
    @DisplayName("C.3 éxito y alterno: con el límite máximo se recorren las 10.000 cartas sin repetidos ni omitidos, y cada respuesta trae como máximo 100")
    void traversesEverything() throws Exception {
        Set<String> seen = new HashSet<>();
        List<Long> millis = new ArrayList<>();
        int pages = 0;
        String cursor = null;
        do {
            long start = System.nanoTime();
            HttpResponse<String> response = get("limit=" + maxLimit + (cursor == null ? "" : "&cursor=" + cursor));
            millis.add((System.nanoTime() - start) / NANOS_PER_MILLI);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode page = json.readTree(response.body());
            JsonNode items = page.get("items");
            assertThat(items.size()).isLessThanOrEqualTo(maxLimit);
            items.valueStream().forEach(item -> assertThat(seen.add(item.get("code").asString())).isTrue());
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
            pages++;
        } while (cursor != null);

        assertThat(seen).hasSize(SEEDED_CARDS);
        assertThat(new HashSet<>(jdbc.queryForList("SELECT code FROM card", String.class))).isEqualTo(seen);
        System.out.printf("Recorrido completo: %d páginas de %d, p95 = %d ms%n", pages, maxLimit, percentile(millis));
    }

    // =========================================================================================
    // Ayudantes.
    // =========================================================================================

    private String plan(CardSearchCriteria criteria, long afterId) {
        CardSearchSql.Query query = CardSearchSql.build(criteria, afterId, maxLimit + 1);
        return String.join("\n", jdbc.queryForList("EXPLAIN " + query.sql(), String.class,
                query.arguments().toArray()));
    }

    private static long percentile(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * PERCENTILE) - 1);
    }

    private void clearCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }

    private HttpResponse<String> get(String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/cartas" + (query.isEmpty() ? "" : "?" + query)))
                .header("Authorization", "Bearer " + admin).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
