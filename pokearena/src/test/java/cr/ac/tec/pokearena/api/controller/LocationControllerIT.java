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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.infrastructure.persistence.JdbcLocationRepository;
import cr.ac.tec.pokearena.infrastructure.storage.ImageIoProcessor;
import cr.ac.tec.pokearena.support.FaultyImageProcessor;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestImages;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de la historia 2.2.1 "Crear ubicación", una por cada criterio de C.2.
 * Responsabilidades: con el servidor real, PostgreSQL de pruebas y una carpeta de imágenes temporal,
 * comprobar los criterios de éxito, error y alternos, más los requisitos de la sección 7.2, con la misma
 * estructura que las pruebas de Crear carta: 401 contra 403, cada error con su status, type y field,
 * ningún rastro de una solicitud inválida, idempotencia, miniatura fallida y compensación de archivos.
 * Referencia: Anexo C.2, sección 7.2 y Anexo A.2.
 *
 * <p>Los fallos se simulan con envoltorios {@code @Primary} que solo existen en este contexto. Cada
 * prueba deja la base como estaba: borra las ubicaciones y los jugadores de prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LocationControllerIT.FaultInjection.class)
@DisplayName("Crear ubicación (2.2.1) de punta a punta")
class LocationControllerIT extends PostgresIntegrationTestBase {

    private static final String PATH = "/api/v1/ubicaciones";
    private static final Path WORKSPACE = createWorkspace();
    private static final Path IMAGES_ROOT = WORKSPACE.resolve("images");

    private static final String TYPE_BONUS = "{\"code\":\"TYPE_BONUS\",\"parameters\":{\"type\":\"FUE\",\"amount\":3}}";

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
    private FaultyImageProcessor imageFaults;

    @Autowired
    private FaultyLocationRepository databaseFaults;

    private String admin;

    @DynamicPropertySource
    static void imagesRoot(DynamicPropertyRegistry registry) {
        registry.add("app.images.root-dir", IMAGES_ROOT::toString);
    }

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM location");
        admin = token(Role.ADMIN);
    }

    @AfterEach
    void cleanUp() throws IOException {
        imageFaults.failThumbnail = false;
        databaseFaults.failOnSave = false;
        jdbc.update("DELETE FROM location");
        TestPlayers.delete(jdbc, createdPlayers);
        deleteContents(IMAGES_ROOT);
    }

    @AfterAll
    static void removeWorkspace() throws IOException {
        deleteContents(WORKSPACE);
        Files.deleteIfExists(WORKSPACE);
    }

    // =========================================================================================
    // Criterios de éxito (C.2)
    // =========================================================================================

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.2 éxito: con nombre, descripción, imagen y un efecto válido, la ubicación queda disponible para partidas nuevas")
        void validLocationWithEffectBecomesAvailable() throws Exception {
            Result result = createLocation(admin, newKey(), fieldsWithEffect(TYPE_BONUS), TestImages.png(800, 600));

            assertThat(result.status()).isEqualTo(201);
            assertThat(result.body().get("available").asBoolean()).isTrue();
            assertThat(result.body().get("effect").get("code").asString()).isEqualTo("TYPE_BONUS");
            assertThat(jdbc.queryForObject("SELECT available FROM location WHERE id = ?", Boolean.class,
                    result.body().get("id").asLong())).isTrue();
        }

        @Test
        @DisplayName("C.2 éxito: sin efecto también queda disponible: el efecto es opcional")
        void validLocationWithoutEffect() throws Exception {
            Result result = createLocation(admin, newKey(), validFields(), TestImages.png(800, 600));

            assertThat(result.status()).isEqualTo(201);
            assertThat(result.body().get("effect").isNull()).isTrue();
            assertThat(result.body().get("available").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("C.2 éxito: límite de cartas de 0 a 5 y energía extra fija (0 a 3) o por cartas (0 a 2)")
        void limitsAndExtraEnergyAreAccepted() throws Exception {
            String[][] cases = { { "0", "NONE", "" }, { "5", "FIXED", "0" }, { "1", "FIXED", "3" },
                    { "2", "PER_CARD", "0" }, { "3", "PER_CARD", "2" } };
            for (String[] values : cases) {
                Map<String, String> fields = validFields();
                fields.put("cardLimit", values[0]);
                fields.put("extraEnergyMode", values[1]);
                fields.put("extraEnergyAmount", values[2]);

                Result result = createLocation(admin, newKey(), fields, TestImages.png(800, 600));

                assertThat(result.status()).as(String.join(" ", values)).isEqualTo(201);
                assertThat(result.body().get("cardLimit").asInt()).isEqualTo(Integer.parseInt(values[0]));
                assertThat(result.body().get("extraEnergy").get("mode").asString()).isEqualTo(values[1]);
            }
        }

        @Test
        @DisplayName("C.2 éxito: catálogo de efectos: bonificación por tipo, modificador de costo (con y sin tipo) y restricción por turno")
        void everyEffectTypeIsAccepted() throws Exception {
            for (String effect : List.of(TYPE_BONUS,
                    "{\"code\":\"COST_MODIFIER\",\"parameters\":{\"amount\":-2}}",
                    "{\"code\":\"COST_MODIFIER\",\"parameters\":{\"amount\":5,\"affectedType\":\"AGU\"}}",
                    "{\"code\":\"TURN_RESTRICTION\",\"parameters\":{\"turns\":[1,3,6]}}")) {
                Result result = createLocation(admin, newKey(), fieldsWithEffect(effect), TestImages.png(800, 600));

                assertThat(result.status()).as(effect).isEqualTo(201);
            }
        }

        @Test
        @DisplayName("C.2 éxito: los parámetros se validan según el tipo: la bonificación por tipo exige un tipo existente y un monto")
        void parametersAreValidatedByType() throws Exception {
            for (String effect : List.of("{\"code\":\"TYPE_BONUS\",\"parameters\":{\"type\":\"FUE\"}}",
                    "{\"code\":\"TYPE_BONUS\",\"parameters\":{\"amount\":2}}",
                    "{\"code\":\"TYPE_BONUS\",\"parameters\":{\"type\":\"XXX\",\"amount\":2}}")) {
                assertError(createLocation(admin, newKey(), fieldsWithEffect(effect), TestImages.png(800, 600)), 400,
                        "INVALID_EFFECT", "effect");
            }
            assertNoTrace();
        }

        @Test
        @DisplayName("C.2 éxito: la imagen PNG, JPG o WebP de 4:3 y al menos 800 × 600 se guarda como versión grande")
        void acceptsPngJpgAndWebp() throws Exception {
            for (byte[] image : List.of(TestImages.png(800, 600), TestImages.jpeg(1200, 900), TestImages.webpLocation())) {
                Result result = createLocation(admin, newKey(), validFields(), image);

                assertThat(result.status()).isEqualTo(201);
                assertThat(get(result.body().get("imageLargeUrl").asString()).body()).isEqualTo(image);
            }
        }

        @Test
        @DisplayName("C.2 éxito: se genera la versión pequeña de 320 × 240 y la ubicación solo se asigna con ambas versiones")
        void thumbnailIsGeneratedAndBothVersionsExist() throws Exception {
            Result result = createLocation(admin, newKey(), validFields(), TestImages.png(800, 600));

            byte[] small = get(result.body().get("imageSmallUrl").asString()).body();
            assertThat(TestImages.read(small).getWidth()).isEqualTo(320);
            assertThat(TestImages.read(small).getHeight()).isEqualTo(240);
            assertThat(result.body().get("available").asBoolean()).isTrue();
        }
    }

    // =========================================================================================
    // Criterios de error (C.2)
    // =========================================================================================

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.2 error: sin credenciales responde 401 y sin rol de administrador 403: errores distinguibles")
        void credentialsAndRoleAreDistinguishable() throws Exception {
            Result unauthenticated = createLocation(null, null, validFields(), TestImages.png(800, 600));
            Result forbidden = createLocation(token(Role.PLAYER), newKey(), validFields(), TestImages.png(800, 600));

            assertError(unauthenticated, 401, "NOT_AUTHENTICATED", null);
            assertError(forbidden, 403, "FORBIDDEN", null);
            assertNoTrace();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = { "{\"code\":\"MAGIC_FIELD\"}",
                "{\"code\":\"TYPE_BONUS\",\"parameters\":{\"type\":\"FUE\",\"amount\":6}}",
                "{\"code\":\"COST_MODIFIER\",\"parameters\":{\"amount\":1,\"affectedType\":\"XXX\"}}",
                "{\"code\":\"TURN_RESTRICTION\",\"parameters\":{\"turns\":[2,2]}}",
                "{\"code\":\"TURN_RESTRICTION\",\"parameters\":{\"turns\":[]}}", "esto no es un json" })
        @DisplayName("C.2 error: un tipo de efecto inexistente o con parámetros inválidos se rechaza y la ubicación no se guarda")
        void invalidEffectIsRejected(String effect) throws Exception {
            assertError(createLocation(admin, newKey(), fieldsWithEffect(effect), TestImages.png(800, 600)), 400,
                    "INVALID_EFFECT", "effect");
            assertNoTrace();
        }

        @Test
        @DisplayName("C.2 error: un efecto inexistente informa los tipos de efecto permitidos")
        void unknownEffectListsAllowedTypes() throws Exception {
            Result result = createLocation(admin, newKey(), fieldsWithEffect("{\"code\":\"MAGIC_FIELD\"}"),
                    TestImages.png(800, 600));

            assertThat(result.body().get("allowedValues").toString())
                    .contains("TYPE_BONUS", "COST_MODIFIER", "TURN_RESTRICTION");
        }

        @ParameterizedTest(name = "{0}={1} ({2})")
        @CsvSource(nullValues = "NULO", value = { "cardLimit, 6, NONE, NULO, 0..5", "cardLimit, -1, NONE, NULO, 0..5",
                "cardLimit, abc, NONE, NULO, 0..5", "extraEnergyMode, 1, DOBLE, 1, NONE",
                "extraEnergyAmount, 1, FIXED, 4, 0..3", "extraEnergyAmount, 1, PER_CARD, 3, 0..2",
                "extraEnergyAmount, 1, NONE, 2, 0" })
        @DisplayName("C.2 error: un límite fuera de 0 a 5, una modalidad inexistente o un monto fuera de su rango se rechaza indicando el campo")
        void invalidFieldIsRejected(String field, String limit, String mode, String amount, String allowed)
                throws Exception {
            Map<String, String> fields = validFields();
            fields.put("cardLimit", limit);
            fields.put("extraEnergyMode", mode);
            if (amount != null) {
                fields.put("extraEnergyAmount", amount);
            }

            Result result = createLocation(admin, newKey(), fields, TestImages.png(800, 600));

            assertError(result, 400, "INVALID_FIELD", field);
            assertThat(result.body().get("allowedValues").toString()).contains(allowed);
            assertNoTrace();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "texto, INVALID_IMAGE_FORMAT", "grande, IMAGE_TOO_LARGE", "vertical, INVALID_IMAGE_RATIO",
                "pequena, IMAGE_RESOLUTION_TOO_LOW" })
        @DisplayName("C.2 error: una imagen de formato no permitido, de más de 5 MB, sin proporción 4:3 o menor a 800 × 600 se rechaza y la ubicación no se guarda")
        void invalidImageIsRejected(String kind, String expectedType) throws Exception {
            byte[] image = switch (kind) {
                case "texto" -> "un archivo de texto con extensión .png".getBytes(StandardCharsets.UTF_8);
                case "grande" -> new byte[(int) ImageRules.MAX_FILE_BYTES + 1];
                case "vertical" -> TestImages.png(600, 840);
                default -> TestImages.png(400, 300);
            };

            assertError(createLocation(admin, newKey(), validFields(), image), 400, expectedType, "image");
            assertNoTrace();
        }

        @Test
        @DisplayName("7.2: nombre, descripción e imagen son obligatorios (MISSING_FIELD)")
        void requiredFields() throws Exception {
            for (String field : List.of("name", "description")) {
                Map<String, String> fields = validFields();
                fields.remove(field);
                assertError(createLocation(admin, newKey(), fields, TestImages.png(800, 600)), 400, "MISSING_FIELD",
                        field);
            }
            assertError(createLocation(admin, newKey(), validFields(), null), 400, "MISSING_FIELD", "image");
            assertNoTrace();
        }

        @Test
        @DisplayName("5.4: sin Idempotency-Key responde 400 IDEMPOTENCY_KEY_REQUIRED")
        void idempotencyKeyIsRequired() throws Exception {
            assertError(createLocation(admin, null, validFields(), TestImages.png(800, 600)), 400,
                    "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key");
            assertNoTrace();
        }

        @Test
        @DisplayName("7.2: si la miniatura falla, la ubicación no queda disponible y el administrador ve PENDING_RETRY")
        void thumbnailFailureLeavesLocationUnavailable() throws Exception {
            imageFaults.failThumbnail = true;

            Result result = createLocation(admin, newKey(), validFields(), TestImages.png(800, 600));

            assertThat(result.status()).isEqualTo(201);
            assertThat(result.body().get("available").asBoolean()).isFalse();
            assertThat(result.body().get("imageStatus").asString()).isEqualTo("PENDING_RETRY");
            assertThat(result.body().get("imageSmallUrl").isNull()).isTrue();
        }

        @Test
        @DisplayName("7.2: si falla el guardado en la base, se borran los archivos guardados y no queda la clave")
        void databaseFailureCompensatesFiles() throws Exception {
            databaseFaults.failOnSave = true;

            assertError(createLocation(admin, newKey(), validFields(), TestImages.png(800, 600)), 500,
                    "INTERNAL_ERROR", null);
            assertNoTrace();
        }
    }

    // =========================================================================================
    // Criterios alternos (C.2)
    // =========================================================================================

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.2 alterno: reenviar la misma solicitud no crea una ubicación duplicada")
        void retryWithSameKey() throws Exception {
            String key = newKey();
            Result first = createLocation(admin, key, fieldsWithEffect(TYPE_BONUS), TestImages.png(800, 600));
            Result retry = createLocation(admin, key, fieldsWithEffect(TYPE_BONUS), TestImages.png(800, 600));

            assertThat(retry.status()).isEqualTo(201);
            assertThat(retry.raw()).isEqualTo(first.raw());
            assertThat(locationRows()).isEqualTo(1);
            assertThat(fileCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("C.2 alterno: crear una ubicación mientras hay partidas en curso no las altera: no toca ninguna otra tabla")
        void touchesNoOtherTable() throws Exception {
            Map<String, Integer> before = rowsPerTable();

            createLocation(admin, newKey(), fieldsWithEffect(TYPE_BONUS), TestImages.png(800, 600));

            Map<String, Integer> after = rowsPerTable();
            // Solo cambian la tabla de ubicaciones y la de claves de idempotencia, en una fila cada una.
            assertThat(after.get("location")).isEqualTo(before.get("location") + 1);
            assertThat(after.get("idempotency_key")).isEqualTo(before.get("idempotency_key") + 1);
            after.remove("location");
            after.remove("idempotency_key");
            before.remove("location");
            before.remove("idempotency_key");
            assertThat(after).isEqualTo(before);
        }

        @Test
        @DisplayName("5.4: la misma clave con otro contenido responde 422 IDEMPOTENCY_KEY_REUSED")
        void sameKeyOtherContent() throws Exception {
            String key = newKey();
            createLocation(admin, key, validFields(), TestImages.png(800, 600));
            Map<String, String> other = validFields();
            other.put("cardLimit", "4");

            assertError(createLocation(admin, key, other, TestImages.png(800, 600)), 422, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key");
            assertThat(locationRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("5.4: dos solicitudes iguales a la vez producen una sola ubicación y la misma respuesta, sin 500")
        void twoIdenticalRequestsAtOnce() throws Exception {
            String key = newKey();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Result>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> createLocation(admin, key, validFields(), TestImages.png(800, 600))));
                }
                Result first = futures.get(0).get();
                Result second = futures.get(1).get();

                assertThat(first.status()).isEqualTo(201);
                assertThat(second.status()).isEqualTo(201);
                assertThat(second.raw()).isEqualTo(first.raw());
                assertThat(locationRows()).isEqualTo(1);
                assertThat(fileCount()).isEqualTo(2);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // =========================================================================================
    // Fallos simulados: envoltorios que solo existen en este contexto.
    // =========================================================================================

    static class FaultInjection {

        @Bean
        @Primary
        FaultyImageProcessor faultyImageProcessor(ImageIoProcessor real) {
            return new FaultyImageProcessor(real);
        }

        @Bean
        @Primary
        FaultyLocationRepository faultyLocationRepository(JdbcLocationRepository real) {
            return new FaultyLocationRepository(real);
        }
    }

    /** Repositorio real que puede simular que la base falla al guardar la ubicación. */
    static class FaultyLocationRepository implements LocationRepository {
        private final LocationRepository real;
        volatile boolean failOnSave;

        FaultyLocationRepository(LocationRepository real) {
            this.real = real;
        }

        @Override
        public long save(Location location) {
            if (failOnSave) {
                throw new IllegalStateException("Falla simulada de la base al guardar la ubicación");
            }
            return real.save(location);
        }
    }

    // =========================================================================================
    // Ayudantes.
    // =========================================================================================

    private static Map<String, String> validFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "Volcán");
        fields.put("description", "Un volcán activo");
        fields.put("cardLimit", "3");
        fields.put("extraEnergyMode", "FIXED");
        fields.put("extraEnergyAmount", "2");
        return fields;
    }

    private static Map<String, String> fieldsWithEffect(String effect) {
        Map<String, String> fields = validFields();
        fields.put("effect", effect);
        return fields;
    }

    private String token(Role role) {
        UUID player = TestPlayers.create(accounts).id();
        createdPlayers.add(player);
        return tokenIssuer.issue(new AuthenticatedUser(player, role)).value();
    }

    private static String newKey() {
        return "clave-" + UUID.randomUUID();
    }

    private Integer locationRows() {
        return jdbc.queryForObject("SELECT count(*) FROM location", Integer.class);
    }

    /** Filas de cada tabla del esquema public, para comprobar qué tablas cambian. */
    private Map<String, Integer> rowsPerTable() {
        Map<String, Integer> rows = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name", String.class)) {
            rows.put(table, jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class));
        }
        return rows;
    }

    private long fileCount() throws IOException {
        if (!Files.exists(IMAGES_ROOT)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(IMAGES_ROOT)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    /** Una solicitud inválida no deja filas, ni archivos, ni claves (sección 7). */
    private void assertNoTrace() throws IOException {
        assertThat(locationRows()).isZero();
        assertThat(fileCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_key WHERE endpoint LIKE '%/ubicaciones'",
                Integer.class)).isZero();
    }

    private static void assertError(Result result, int status, String type, String field) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.body().get("type").asString()).isEqualTo(type);
        assertThat(result.body().get("correlationId").asString()).isNotBlank();
        if (field != null) {
            assertThat(result.body().get("field").asString()).isEqualTo(field);
        }
    }

    private Result createLocation(String bearer, String key, Map<String, String> fields, byte[] image)
            throws IOException, InterruptedException {
        String boundary = "limite-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (image != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"ubicacion.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(image);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body(), json.readTree(response.body()));
    }

    private HttpResponse<byte[]> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static void deleteContents(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(folder)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path createWorkspace() {
        try {
            return Files.createTempDirectory("pokearena-ubicaciones-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Result(int status, String raw, JsonNode body) {
    }
}
