package cr.ac.tec.pokearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar de punta a punta la idempotencia de los POST de creación (sección 5.4).
 * Responsabilidades: con el servidor real, una operación de prueba que sigue la misma plantilla que
 * usarán Crear carta y Crear ubicación, y jugadores reales, comprobar: el reintento sin repetir la
 * operación, la misma clave con otro contenido (422), solicitudes simultáneas (un solo efecto y la
 * misma respuesta para todas, sin 500), claves de usuarios u operaciones distintas y el encabezado.
 * Referencia: sección 5.4 y criterios alternos de C.1 y C.2 ("reenviar la misma solicitud no crea
 * una entidad duplicada").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ IdempotencyIT.CountingCreateOperation.class, IdempotencyIT.IdempotentTestController.class })
@DisplayName("Idempotencia de los POST de creación")
class IdempotencyIT extends PostgresIntegrationTestBase {

    private static final String BASE_PATH = "/test/idempotencia";
    private static final String HEADER = "Idempotency-Key";
    private static final int CONCURRENT_REQUESTS = 5;
    private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(20);
    private static final String TEST_SCHEMA = "prueba";
    private static final String EFFECT_TABLE = TEST_SCHEMA + ".idempotency_test_effect";

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

    @Autowired
    private CountingCreateOperation operation;

    @Value("${app.idempotency.max-key-length}")
    private int maxKeyLength;

    private String token;

    @BeforeEach
    void prepare() {
        // Tabla solo de esta prueba, que representa la entidad que crea la operación. Va en un esquema
        // aparte para no alterar el esquema public, que FlywayMigrationIT verifica tabla por tabla.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + TEST_SCHEMA);
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + EFFECT_TABLE + " (id BIGSERIAL PRIMARY KEY, name TEXT NOT NULL)");
        jdbc.execute("TRUNCATE " + EFFECT_TABLE);
        operation.reset();
        token = tokenFor(newPlayer());
    }

    @AfterEach
    void cleanUp() {
        TestPlayers.delete(jdbc, createdPlayers);
        jdbc.execute("DROP TABLE IF EXISTS " + EFFECT_TABLE);
    }

    @Test
    @DisplayName("Reintentar con la misma clave y el mismo contenido devuelve la misma respuesta sin repetir la operación")
    void retryReplaysResponse() throws Exception {
        Result first = send(token, "clave-reintento", "Pikachu", "imagen-1");
        Result retry = send(token, "clave-reintento", "Pikachu", "imagen-1");

        assertThat(first.status()).isEqualTo(201);
        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.raw()).isEqualTo(first.raw());
        assertThat(operation.executions.get()).isEqualTo(1);
        assertThat(effectRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("La misma clave con otro campo es rechazada con 422 IDEMPOTENCY_KEY_REUSED, sin ejecutar nada")
    void sameKeyOtherFieldIsRejected() throws Exception {
        send(token, "clave-otra", "Pikachu", "imagen-1");

        Result other = send(token, "clave-otra", "Raichu", "imagen-1");

        assertError(other, 422, "IDEMPOTENCY_KEY_REUSED");
        assertThat(operation.executions.get()).isEqualTo(1);
        assertThat(effectRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("La misma clave con otro archivo también es otro contenido: 422")
    void sameKeyOtherFileIsRejected() throws Exception {
        send(token, "clave-archivo", "Pikachu", "imagen-1");

        assertError(send(token, "clave-archivo", "Pikachu", "imagen-2"), 422, "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("Solicitudes iguales y simultáneas producen un solo efecto y todas reciben la misma respuesta, sin 500")
    void concurrentRequestsProduceOneEffect() throws Exception {
        // Todas pasan la consulta de idempotencia antes de que alguna confirme su transacción.
        operation.arrival = new CyclicBarrier(CONCURRENT_REQUESTS);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> send(token, "clave-simultanea", "Pikachu", "imagen-1")));
            }
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get());
            }

            assertThat(results).extracting(Result::status).containsOnly(201);
            assertThat(results).extracting(Result::raw).containsOnly(results.getFirst().raw());
            // Todas ejecutaron la operación, pero solo una confirmó: un solo efecto.
            assertThat(operation.executions.get()).isEqualTo(CONCURRENT_REQUESTS);
            assertThat(effectRows()).isEqualTo(1);
            // Las que perdieron compensaron su efecto previo: queda un solo "archivo".
            assertThat(operation.compensations.get()).isEqualTo(CONCURRENT_REQUESTS - 1);
            assertThat(operation.files).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("La misma clave de usuarios distintos no choca: cada uno crea lo suyo")
    void keysOfDifferentUsersDoNotCollide() throws Exception {
        String otherToken = tokenFor(newPlayer());

        Result mine = send(token, "clave-compartida", "Pikachu", "imagen-1");
        Result theirs = send(otherToken, "clave-compartida", "Pikachu", "imagen-1");

        assertThat(mine.status()).isEqualTo(201);
        assertThat(theirs.status()).isEqualTo(201);
        assertThat(theirs.body().get("id").asLong()).isNotEqualTo(mine.body().get("id").asLong());
        assertThat(effectRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("La misma clave en otra operación no choca")
    void keysOfDifferentEndpointsDoNotCollide() throws Exception {
        assertThat(send(token, "clave-operaciones", "Pikachu", "imagen-1").status()).isEqualTo(201);
        assertThat(send(token, "/otros", "clave-operaciones", "Pikachu", "imagen-1").status()).isEqualTo(201);

        assertThat(effectRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin el encabezado Idempotency-Key responde 400 IDEMPOTENCY_KEY_REQUIRED, sin ejecutar nada")
    void missingHeaderIsRequired() throws Exception {
        assertError(send(token, null, "Pikachu", "imagen-1"), 400, "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(operation.executions.get()).isZero();
    }

    @Test
    @DisplayName("Un encabezado Idempotency-Key vacío responde 400 IDEMPOTENCY_KEY_REQUIRED")
    void blankHeaderIsRequired() throws Exception {
        assertError(send(token, "   ", "Pikachu", "imagen-1"), 400, "IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("Una clave más larga que el máximo configurado responde 400 INVALID_FIELD con el límite")
    void tooLongKeyIsInvalid() throws Exception {
        Result result = send(token, "k".repeat(maxKeyLength + 1), "Pikachu", "imagen-1");

        assertError(result, 400, "INVALID_FIELD");
        assertThat(result.body().get("field").asString()).isEqualTo(HEADER);
        assertThat(result.body().get("allowedValues").get(0).asString()).isEqualTo("1.." + maxKeyLength);
        assertThat(operation.executions.get()).isZero();
    }

    @Test
    @DisplayName("Sin credenciales responde 401 aunque también falte la clave: primero la autorización")
    void authorizationComesFirst() throws Exception {
        assertError(send(null, null, "Pikachu", "imagen-1"), 401, "NOT_AUTHENTICATED");
    }

    // ---------------------------------------------------------------------------------------
    // Operación y controlador SOLO de pruebas, anidados para que Spring no los encuentre al escanear.
    // ---------------------------------------------------------------------------------------

    /** Resultado de la operación de prueba; se guarda y se repite como cuerpo de la respuesta. */
    public record SampleResult(long id, String name) {
    }

    /**
     * Operación de creación de prueba con la misma plantilla que usarán los casos de uso reales:
     * consulta (paso 4), efecto previo fuera de la transacción (como guardar los archivos, paso 9),
     * transacción con la entidad y la clave (paso 10) y compensación si pierde la carrera.
     */
    static class CountingCreateOperation {

        final AtomicInteger executions = new AtomicInteger();
        final AtomicInteger compensations = new AtomicInteger();
        final Set<String> files = ConcurrentHashMap.newKeySet();
        volatile CyclicBarrier arrival;

        private final IdempotencyGuard guard;
        private final UnitOfWork unitOfWork;
        private final JdbcTemplate jdbc;

        CountingCreateOperation(IdempotencyGuard guard, UnitOfWork unitOfWork, JdbcTemplate jdbc) {
            this.guard = guard;
            this.unitOfWork = unitOfWork;
            this.jdbc = jdbc;
        }

        void reset() {
            executions.set(0);
            compensations.set(0);
            files.clear();
            arrival = null;
        }

        IdempotentResult<SampleResult> create(IdempotentRequest request, String name) throws Exception {
            // Paso 4: solicitud previa con la clave.
            Optional<IdempotentResult<SampleResult>> previous = guard.findPrevious(request, SampleResult.class);
            if (previous.isPresent()) {
                return previous.get();
            }
            CyclicBarrier barrier = arrival;
            if (barrier != null) {
                barrier.await(BARRIER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }
            executions.incrementAndGet();

            // Efecto previo a la transacción (en la historia real, guardar los archivos de imagen).
            String file = "archivo-" + UUID.randomUUID();
            files.add(file);
            try {
                // Paso 10: la entidad y la clave en la misma transacción.
                SampleResult result = unitOfWork.execute(() -> {
                    Long id = jdbc.queryForObject("INSERT INTO " + EFFECT_TABLE + " (name) VALUES (?) RETURNING id",
                            Long.class, name);
                    SampleResult created = new SampleResult(id, name);
                    guard.record(request, created);
                    return created;
                });
                return IdempotentResult.executed(request, result);
            } catch (IdempotencyConflictException conflict) {
                // Otra solicitud igual confirmó primero: se borra el efecto previo y se repite su respuesta.
                compensate(file);
                return guard.resolveConflict(request, SampleResult.class);
            } catch (RuntimeException failure) {
                compensate(file);
                throw failure;
            }
        }

        private void compensate(String file) {
            files.remove(file);
            compensations.incrementAndGet();
        }
    }

    @RestController
    @RequestMapping(BASE_PATH)
    @RequiresRole(Role.PLAYER)
    static class IdempotentTestController {

        private final CountingCreateOperation operation;

        IdempotentTestController(CountingCreateOperation operation) {
            this.operation = operation;
        }

        @PostMapping(path = "/recursos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        ResponseEntity<SampleResult> create(AuthenticatedUser user, IdempotencyKeyHeader key,
                @RequestParam String name, @RequestParam MultipartFile file) throws Exception {
            return respond(user, key, name, file);
        }

        @PostMapping(path = "/otros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        ResponseEntity<SampleResult> createOther(AuthenticatedUser user, IdempotencyKeyHeader key,
                @RequestParam String name, @RequestParam MultipartFile file) throws Exception {
            return respond(user, key, name, file);
        }

        private ResponseEntity<SampleResult> respond(AuthenticatedUser user, IdempotencyKeyHeader key, String name,
                MultipartFile file) throws Exception {
            // El hash incluye los campos y el contenido del archivo.
            String hash = RequestHash.builder().field("name", name).file("file", file.getBytes()).build();
            IdempotentResult<SampleResult> result = operation.create(
                    key.toRequest(user.playerId(), hash, HttpStatus.CREATED.value()), name);
            return ResponseEntity.status(result.status()).body(result.body());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes.
    // ---------------------------------------------------------------------------------------

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        createdPlayers.add(id);
        return id;
    }

    private String tokenFor(UUID playerId) {
        return tokenIssuer.issue(new AuthenticatedUser(playerId, Role.PLAYER)).value();
    }

    private Integer effectRows() {
        return jdbc.queryForObject("SELECT count(*) FROM " + EFFECT_TABLE, Integer.class);
    }

    private Result send(String bearer, String key, String name, String fileContent) throws IOException,
            InterruptedException {
        return send(bearer, "/recursos", key, name, fileContent);
    }

    private Result send(String bearer, String path, String key, String name, String fileContent)
            throws IOException, InterruptedException {
        String boundary = "limite-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n" + name + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"imagen.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n" + fileContent + "\r\n--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE_PATH + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (key != null) {
            request.header(HEADER, key);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body(), json.readTree(response.body()));
    }

    private static void assertError(Result result, int status, String type) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
        assertThat(result.body().get("correlationId").asString()).isNotBlank();
    }

    private record Result(int status, String raw, JsonNode body) {
    }
}
