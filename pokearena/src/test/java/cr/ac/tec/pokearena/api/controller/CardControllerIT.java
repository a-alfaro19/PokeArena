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
import java.util.Optional;
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
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.CardRepository;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.infrastructure.persistence.JdbcCardRepository;
import cr.ac.tec.pokearena.infrastructure.storage.ImageIoProcessor;
import cr.ac.tec.pokearena.support.FaultyImageProcessor;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestImages;
import cr.ac.tec.pokearena.support.TestPlayers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: pruebas de punta a punta de la historia 2.1.1 "Crear carta", una por cada criterio de C.1.
 * Responsabilidades: con el servidor real, PostgreSQL de pruebas y una carpeta de imágenes temporal,
 * comprobar los criterios de éxito, error y alternos, más los requisitos de la sección 7.1: 401 contra
 * 403, cada error con su status, type y field, ningún rastro de una solicitud inválida, idempotencia,
 * concurrencia, miniatura fallida y compensación de archivos.
 * Referencia: Anexo C.1, sección 7.1 y Anexo A.1.
 *
 * <p>Los fallos se simulan con dos envoltorios {@code @Primary} que solo existen en este contexto y
 * delegan en las implementaciones reales: uno hace fallar la miniatura y otro el guardado en la base.
 * Cada prueba deja la base como estaba: borra las cartas, las habilidades de prueba y los jugadores, y
 * vuelve los consecutivos a 0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CardControllerIT.FaultInjection.class)
@DisplayName("Crear carta (2.1.1) de punta a punta")
class CardControllerIT extends PostgresIntegrationTestBase {

    private static final String PATH = "/api/v1/cartas";
    private static final Path WORKSPACE = createWorkspace();
    private static final Path IMAGES_ROOT = WORKSPACE.resolve("images");
    private static final int CONCURRENT_ADMINS_REQUESTS = 20;

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
    private FaultyCardRepository databaseFaults;

    private String admin;

    @DynamicPropertySource
    static void imagesRoot(DynamicPropertyRegistry registry) {
        registry.add("app.images.root-dir", IMAGES_ROOT::toString);
    }

    @BeforeEach
    void prepare() {
        resetCatalog();
        admin = token(Role.ADMIN);
    }

    @AfterEach
    void cleanUp() throws IOException {
        imageFaults.failThumbnail = false;
        databaseFaults.failOnSave = false;
        resetCatalog();
        TestPlayers.delete(jdbc, createdPlayers);
        deleteContents(IMAGES_ROOT);
    }

    @AfterAll
    static void removeWorkspace() throws IOException {
        deleteContents(WORKSPACE);
        Files.deleteIfExists(WORKSPACE);
    }

    // =========================================================================================
    // Criterios de éxito (C.1)
    // =========================================================================================

    @Nested
    @DisplayName("Éxito")
    class Success {

        @Test
        @DisplayName("C.1 éxito: con nombre, imagen, costo, poder, tipo, rareza y habilidad, la carta queda disponible en el catálogo")
        void validCardBecomesAvailable() throws Exception {
            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            assertThat(result.status()).isEqualTo(201);
            assertThat(result.body().get("available").asBoolean()).isTrue();
            assertThat(result.body().get("imageStatus").asString()).isEqualTo("READY");
            assertThat(jdbc.queryForObject("SELECT available FROM card WHERE code = ?", Boolean.class,
                    result.body().get("code").asString())).isTrue();
        }

        @Test
        @DisplayName("C.1 éxito: la imagen PNG, JPG o WebP de 5:7 y al menos 600 × 840 se guarda como versión grande, sin cambios")
        void acceptsPngJpgAndWebpAsLargeVersion() throws Exception {
            for (byte[] image : List.of(TestImages.png(600, 840), TestImages.jpeg(1000, 1400), TestImages.webpCard())) {
                Result result = createCard(admin, newKey(), validFields(), image);

                assertThat(result.status()).isEqualTo(201);
                assertThat(get(result.body().get("imageLargeUrl").asString()).body()).isEqualTo(image);
            }
        }

        @Test
        @DisplayName("C.1 éxito: se genera la versión pequeña de 200 × 280 y la carta solo está disponible con ambas versiones")
        void thumbnailIsGeneratedAndBothVersionsExist() throws Exception {
            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            byte[] small = get(result.body().get("imageSmallUrl").asString()).body();
            assertThat(TestImages.read(small).getWidth()).isEqualTo(200);
            assertThat(TestImages.read(small).getHeight()).isEqualTo(280);
            assertThat(result.body().get("available").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("C.1 éxito: el código es PKA-<TIPO>-<NNN>, único y con un consecutivo de 3 dígitos por tipo")
        void codeFollowsFormatAndSequencePerType() throws Exception {
            assertThat(code(createCard(admin, newKey(), fields("FUE"), TestImages.png(600, 840)))).isEqualTo("PKA-FUE-001");
            assertThat(code(createCard(admin, newKey(), fields("FUE"), TestImages.png(600, 840)))).isEqualTo("PKA-FUE-002");
            assertThat(code(createCard(admin, newKey(), fields("AGU"), TestImages.png(600, 840)))).isEqualTo("PKA-AGU-001");
        }

        @Test
        @DisplayName("C.1 éxito: valores permitidos: costo 0 y 10, poder -20 y 20, los 6 tipos y las 4 rarezas")
        void allowedValuesAreAccepted() throws Exception {
            String[] types = { "FUE", "AGU", "PLA", "ELE", "PSI", "LUC" };
            String[] rarities = { "COMMON", "UNCOMMON", "RARE", "LEGENDARY" };
            String[][] extremes = { { "0", "-20" }, { "10", "20" } };
            for (int i = 0; i < types.length; i++) {
                Map<String, String> fields = fields(types[i]);
                fields.put("rarity", rarities[i % rarities.length]);
                fields.put("cost", extremes[i % extremes.length][0]);
                fields.put("power", extremes[i % extremes.length][1]);

                Result result = createCard(admin, newKey(), fields, TestImages.png(600, 840));

                assertThat(result.status()).as(types[i]).isEqualTo(201);
                assertThat(result.body().get("type").get("code").asString()).isEqualTo(types[i]);
            }
        }

        @Test
        @DisplayName("C.1 éxito: la habilidad se elige por código y Sin Habilidad (HAB-000) existe desde el inicio")
        void noAbilityExistsFromTheStart() throws Exception {
            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            assertThat(result.body().get("ability").get("code").asString()).isEqualTo("HAB-000");
            assertThat(result.body().get("ability").get("name").asString()).isEqualTo("Sin Habilidad");
        }

        @Test
        @DisplayName("C.1 éxito: la ficha muestra el tipo, la habilidad y a qué tipo está limitada")
        void detailShowsTypeAbilityAndLimitedType() throws Exception {
            seedAbility("HAB-T-LIM", "Corriente marina", "AGU", null);
            Map<String, String> fields = fields("AGU");
            fields.put("ability", "HAB-T-LIM");

            JsonNode card = createCard(admin, newKey(), fields, TestImages.png(600, 840)).body();

            assertThat(card.get("type").get("name").asString()).isEqualTo("Agua");
            assertThat(card.get("ability").get("name").asString()).isEqualTo("Corriente marina");
            assertThat(card.get("ability").get("limitedToType").asString()).isEqualTo("AGU");
        }
    }

    // =========================================================================================
    // Criterios de error (C.1)
    // =========================================================================================

    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("C.1 error: sin credenciales responde 401 NOT_AUTHENTICATED, aunque también falte la clave")
        void withoutCredentials() throws Exception {
            Result result = createCard(null, null, validFields(), TestImages.png(600, 840));

            assertError(result, 401, "NOT_AUTHENTICATED", null);
            assertNoTrace();
        }

        @Test
        @DisplayName("C.1 error: un usuario sin rol de administrador recibe 403 FORBIDDEN, distinto del 401")
        void playerIsForbidden() throws Exception {
            Result forbidden = createCard(token(Role.PLAYER), newKey(), validFields(), TestImages.png(600, 840));
            Result unauthenticated = createCard(null, newKey(), validFields(), TestImages.png(600, 840));

            assertError(forbidden, 403, "FORBIDDEN", null);
            assertThat(forbidden.body().get("type").asString()).isNotEqualTo(unauthenticated.body().get("type").asString());
            assertNoTrace();
        }

        @ParameterizedTest(name = "{0}={1}")
        @CsvSource({ "cost, 11, 0..10", "cost, -1, 0..10", "cost, abc, 0..10", "power, 21, -20..20",
                "power, -21, -20..20", "type, XXX, AGU", "rarity, EPIC, COMMON" })
        @DisplayName("C.1 error: un costo, poder, tipo o rareza fuera de los valores permitidos se rechaza indicando el campo")
        void invalidFieldIsRejected(String field, String value, String expectedAllowed) throws Exception {
            Map<String, String> fields = validFields();
            fields.put(field, value);

            Result result = createCard(admin, newKey(), fields, TestImages.png(600, 840));

            assertError(result, 400, "INVALID_FIELD", field);
            assertThat(result.body().get("allowedValues").toString()).contains(expectedAllowed);
            assertNoTrace();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "texto, INVALID_IMAGE_FORMAT", "grande, IMAGE_TOO_LARGE", "cuadrada, INVALID_IMAGE_RATIO",
                "pequena, IMAGE_RESOLUTION_TOO_LOW" })
        @DisplayName("C.1 error: una imagen de formato no permitido, de más de 5 MB, sin proporción 5:7 o menor a 600 × 840 se rechaza y la carta no se guarda")
        void invalidImageIsRejected(String kind, String expectedType) throws Exception {
            byte[] image = switch (kind) {
                case "texto" -> "un archivo de texto con extensión .png".getBytes(StandardCharsets.UTF_8);
                case "grande" -> new byte[(int) ImageRules.MAX_FILE_BYTES + 1];
                case "cuadrada" -> TestImages.png(600, 600);
                default -> TestImages.png(300, 420);
            };

            Result result = createCard(admin, newKey(), validFields(), image);

            assertError(result, 400, expectedType, "image");
            assertNoTrace();
        }

        @Test
        @DisplayName("C.1 error: si la miniatura falla, la carta no queda disponible y el administrador ve el estado PENDING_RETRY")
        void thumbnailFailureLeavesCardUnavailable() throws Exception {
            imageFaults.failThumbnail = true;

            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            assertThat(result.status()).isEqualTo(201);
            assertThat(result.body().get("available").asBoolean()).isFalse();
            assertThat(result.body().get("imageStatus").asString()).isEqualTo("PENDING_RETRY");
            assertThat(result.body().get("imageSmallUrl").isNull()).isTrue();
            assertThat(jdbc.queryForMap("SELECT available, image_status FROM card WHERE code = ?", code(result)))
                    .containsEntry("available", false).containsEntry("image_status", "PENDING_RETRY");
        }

        @Test
        @DisplayName("C.1 error: una carta sin habilidad se rechaza indicando que debe elegir una, aunque sea Sin Habilidad")
        void missingAbilityIsRejected() throws Exception {
            Map<String, String> fields = validFields();
            fields.remove("ability");

            Result result = createCard(admin, newKey(), fields, TestImages.png(600, 840));

            assertError(result, 400, "MISSING_FIELD", "ability");
            assertThat(result.body().get("detail").asString()).contains("HAB-000");
            assertNoTrace();
        }

        @Test
        @DisplayName("C.1 error: un código de habilidad inexistente se rechaza")
        void unknownAbilityIsRejected() throws Exception {
            Map<String, String> fields = validFields();
            fields.put("ability", "HAB-999");

            assertError(createCard(admin, newKey(), fields, TestImages.png(600, 840)), 400, "UNKNOWN_ABILITY", "ability");
            assertNoTrace();
        }

        @Test
        @DisplayName("C.1 error: una habilidad de evolución cuyo destino es la misma carta se rechaza sin consumir el código")
        void selfEvolutionIsRejected() throws Exception {
            // La habilidad apunta al código que va a recibir la próxima carta de Fuego.
            seedAbility("HAB-T-EVO", "Evoluciona a sí misma", null, nextCode("FUE"));
            Map<String, String> fields = fields("FUE");
            fields.put("ability", "HAB-T-EVO");

            Result result = createCard(admin, newKey(), fields, TestImages.png(600, 840));

            assertError(result, 400, "INVALID_EVOLUTION", "ability");
            assertNoTrace();
        }

        @Test
        @DisplayName("7.1: el nombre y la imagen son obligatorios (MISSING_FIELD)")
        void nameAndImageAreRequired() throws Exception {
            Map<String, String> withoutName = validFields();
            withoutName.remove("name");

            assertError(createCard(admin, newKey(), withoutName, TestImages.png(600, 840)), 400, "MISSING_FIELD", "name");
            assertError(createCard(admin, newKey(), validFields(), null), 400, "MISSING_FIELD", "image");
            assertNoTrace();
        }

        @Test
        @DisplayName("5.4: sin Idempotency-Key responde 400 IDEMPOTENCY_KEY_REQUIRED")
        void idempotencyKeyIsRequired() throws Exception {
            assertError(createCard(admin, null, validFields(), TestImages.png(600, 840)), 400,
                    "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key");
            assertNoTrace();
        }

        @Test
        @DisplayName("7.1: si falla el guardado en la base, se borran los archivos guardados y el consecutivo no se consume")
        void databaseFailureCompensatesFiles() throws Exception {
            databaseFaults.failOnSave = true;

            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            assertError(result, 500, "INTERNAL_ERROR", null);
            assertNoTrace();
        }
    }

    // =========================================================================================
    // Criterios alternos (C.1)
    // =========================================================================================

    @Nested
    @DisplayName("Alternos")
    class Alternatives {

        @Test
        @DisplayName("C.1 alterno: reenviar la misma solicitud no crea una carta duplicada ni consume un código adicional")
        void retryWithSameKey() throws Exception {
            String key = newKey();
            Result first = createCard(admin, key, validFields(), TestImages.png(600, 840));
            Result retry = createCard(admin, key, validFields(), TestImages.png(600, 840));

            assertThat(retry.status()).isEqualTo(201);
            assertThat(retry.raw()).isEqualTo(first.raw());
            assertThat(cardRows()).isEqualTo(1);
            assertThat(counter("FUE")).isEqualTo(1);
            assertThat(fileCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("5.4: la misma clave con otro contenido responde 422 IDEMPOTENCY_KEY_REUSED")
        void sameKeyOtherContent() throws Exception {
            String key = newKey();
            createCard(admin, key, validFields(), TestImages.png(600, 840));
            Map<String, String> other = validFields();
            other.put("cost", "7");

            assertError(createCard(admin, key, other, TestImages.png(600, 840)), 422, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key");
            assertThat(cardRows()).isEqualTo(1);
        }

        @Test
        @DisplayName("5.4: dos solicitudes iguales a la vez producen una sola carta y la misma respuesta, sin 500")
        void twoIdenticalRequestsAtOnce() throws Exception {
            String key = newKey();
            List<Result> results = concurrently(2, i -> createCard(admin, key, validFields(), TestImages.png(600, 840)));

            assertThat(results).extracting(Result::status).containsOnly(201);
            assertThat(results.get(1).raw()).isEqualTo(results.get(0).raw());
            assertThat(cardRows()).isEqualTo(1);
            assertThat(counter("FUE")).isEqualTo(1);
            // La solicitud que perdió borró sus archivos: quedan solo los de la carta creada.
            assertThat(fileCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("C.1 alterno: dos administradores que crean cartas del mismo tipo al mismo tiempo reciben códigos distintos (20 hilos)")
        void concurrentAdminsGetDistinctConsecutiveCodes() throws Exception {
            String otherAdmin = token(Role.ADMIN);
            List<Result> results = concurrently(CONCURRENT_ADMINS_REQUESTS, i -> createCard(i % 2 == 0 ? admin : otherAdmin,
                    newKey(), validFields(), TestImages.png(600, 840)));

            assertThat(results).extracting(Result::status).containsOnly(201);
            List<Integer> numbers = results.stream().map(result -> Integer.parseInt(code(result).substring(8)))
                    .sorted().toList();
            // Distintos y consecutivos: exactamente 1, 2, ..., 20.
            assertThat(numbers).containsExactlyElementsOf(
                    java.util.stream.IntStream.rangeClosed(1, CONCURRENT_ADMINS_REQUESTS).boxed().toList());
            assertThat(counter("FUE")).isEqualTo(CONCURRENT_ADMINS_REQUESTS);
        }

        @Test
        @DisplayName("C.1 alterno: habilidades de evolución que forman un ciclo (A evoluciona a B y B a A) se rechazan")
        void evolutionCycleIsRejected() throws Exception {
            // B = la próxima carta de Agua, con una habilidad que evoluciona a A (la próxima carta de Fuego).
            String cardA = nextCode("FUE");
            seedAbility("HAB-T-HACIA-A", "Evoluciona a A", null, cardA);
            Map<String, String> fieldsB = fields("AGU");
            fieldsB.put("ability", "HAB-T-HACIA-A");
            String cardB = code(createCard(admin, newKey(), fieldsB, TestImages.png(600, 840)));

            // A, con una habilidad que evoluciona a B: cerraría el ciclo A -> B -> A.
            seedAbility("HAB-T-HACIA-B", "Evoluciona a B", null, cardB);
            Map<String, String> fieldsA = fields("FUE");
            fieldsA.put("ability", "HAB-T-HACIA-B");
            Result result = createCard(admin, newKey(), fieldsA, TestImages.png(600, 840));

            assertError(result, 400, "INVALID_EVOLUTION", "ability");
            assertThat(cardRows()).isEqualTo(1);
            assertThat(counter("FUE")).isZero();
        }

        @Test
        @DisplayName("C.1 alterno: la miniatura se genera de forma síncrona (ADR-002): la ficha ya trae la versión pequeña lista")
        void thumbnailIsSynchronous() throws Exception {
            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            assertThat(result.body().get("imageStatus").asString()).isEqualTo("READY");
            assertThat(get(result.body().get("imageSmallUrl").asString()).statusCode()).isEqualTo(200);
        }
    }

    // =========================================================================================
    // Código sin máximo y orden de las validaciones de entrada.
    // =========================================================================================

    @Nested
    @DisplayName("Código de carta sin máximo")
    class CodeWithoutMaximum {

        @Test
        @DisplayName("7.1: la carta número 1000 de un tipo se crea como PKA-FUE-1000")
        void thousandthCard() throws Exception {
            jdbc.update("UPDATE card_type_counter SET last_number = 999 WHERE type_code = 'FUE'");

            Result result = createCard(admin, newKey(), validFields(), TestImages.png(600, 840));

            assertThat(result.status()).isEqualTo(201);
            assertThat(code(result)).isEqualTo("PKA-FUE-1000");
            assertThat(counter("FUE")).isEqualTo(1000);
        }

        @Test
        @DisplayName("7.1: con más de 3 dígitos los consecutivos siguen en orden: 999, 1000, 1001")
        void sequenceKeepsOrderBeyondThreeDigits() throws Exception {
            jdbc.update("UPDATE card_type_counter SET last_number = 998 WHERE type_code = 'FUE'");

            List<String> codes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                codes.add(code(createCard(admin, newKey(), validFields(), TestImages.png(600, 840))));
            }

            assertThat(codes).containsExactly("PKA-FUE-999", "PKA-FUE-1000", "PKA-FUE-1001");
            // El orden de creación (id) coincide con el del consecutivo, aunque como texto "1000" < "999".
            assertThat(jdbc.queryForList("SELECT code FROM card ORDER BY id", String.class)).containsExactlyElementsOf(codes);
        }
    }

    @Nested
    @DisplayName("Primero autenticar: un archivo de más de 5 MB no adelanta ningún error")
    class AuthenticateFirst {

        private final byte[] oversized = new byte[(int) ImageRules.MAX_FILE_BYTES + 1];

        @Test
        @DisplayName("7.1: con un archivo de más de 5 MB y sin token, responde 401 NOT_AUTHENTICATED")
        void oversizedWithoutToken() throws Exception {
            assertError(createCard(null, newKey(), validFields(), oversized), 401, "NOT_AUTHENTICATED", null);
            assertNoTrace();
        }

        @Test
        @DisplayName("7.1: con un archivo de más de 5 MB y token de jugador, responde 403 FORBIDDEN")
        void oversizedWithPlayerToken() throws Exception {
            assertError(createCard(token(Role.PLAYER), newKey(), validFields(), oversized), 403, "FORBIDDEN", null);
            assertNoTrace();
        }

        @Test
        @DisplayName("7.1: con un archivo de más de 5 MB y token de administrador, responde 400 IMAGE_TOO_LARGE")
        void oversizedWithAdminToken() throws Exception {
            assertError(createCard(admin, newKey(), validFields(), oversized), 400, "IMAGE_TOO_LARGE", "image");
            assertNoTrace();
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
        FaultyCardRepository faultyCardRepository(JdbcCardRepository real) {
            return new FaultyCardRepository(real);
        }
    }

    /** Repositorio real que puede simular que la base falla al guardar la carta. */
    static class FaultyCardRepository implements CardRepository {
        private final CardRepository real;
        volatile boolean failOnSave;

        FaultyCardRepository(CardRepository real) {
            this.real = real;
        }

        @Override
        public int nextNumber(String typeCode) {
            return real.nextNumber(typeCode);
        }

        @Override
        public void save(Card card) {
            if (failOnSave) {
                throw new IllegalStateException("Falla simulada de la base al guardar la carta");
            }
            real.save(card);
        }

        @Override
        public Optional<String> evolutionTargetOf(String cardCode) {
            return real.evolutionTargetOf(cardCode);
        }
    }

    // =========================================================================================
    // Ayudantes.
    // =========================================================================================

    private static Map<String, String> fields(String type) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", "Charmander");
        fields.put("cost", "3");
        fields.put("power", "4");
        fields.put("type", type);
        fields.put("rarity", "COMMON");
        fields.put("ability", "HAB-000");
        return fields;
    }

    private static Map<String, String> validFields() {
        return fields("FUE");
    }

    private String token(Role role) {
        UUID player = TestPlayers.create(accounts).id();
        createdPlayers.add(player);
        return tokenIssuer.issue(new AuthenticatedUser(player, role)).value();
    }

    private static String newKey() {
        return "clave-" + UUID.randomUUID();
    }

    private static String code(Result result) {
        return result.body().get("code").asString();
    }

    private void seedAbility(String code, String name, String limitedToType, String evolvesTo) {
        jdbc.update("""
                INSERT INTO ability (code, name, limited_to_type, evolves_to_card_code, created_at)
                VALUES (?, ?, ?, ?, now())
                """, code, name, limitedToType, evolvesTo);
    }

    /** Código que recibirá la próxima carta del tipo, leído del consecutivo de la base. */
    private String nextCode(String type) {
        Integer last = jdbc.queryForObject("SELECT last_number FROM card_type_counter WHERE type_code = ?",
                Integer.class, type);
        return String.format("PKA-%s-%03d", type, last + 1);
    }

    private void resetCatalog() {
        jdbc.update("DELETE FROM card");
        jdbc.update("DELETE FROM ability WHERE code LIKE 'HAB-T-%'");
        jdbc.update("UPDATE card_type_counter SET last_number = 0");
    }

    private Integer cardRows() {
        return jdbc.queryForObject("SELECT count(*) FROM card", Integer.class);
    }

    private Integer counter(String type) {
        return jdbc.queryForObject("SELECT last_number FROM card_type_counter WHERE type_code = ?", Integer.class, type);
    }

    private long fileCount() throws IOException {
        if (!Files.exists(IMAGES_ROOT)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(IMAGES_ROOT)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    /** Una solicitud inválida no deja filas, ni archivos, ni consecutivos, ni claves (sección 7). */
    private void assertNoTrace() throws IOException {
        assertThat(cardRows()).isZero();
        assertThat(fileCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(last_number), 0) FROM card_type_counter", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_key WHERE endpoint LIKE '%/cartas'",
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

    private interface Request {
        Result send(int index) throws Exception;
    }

    private static List<Result> concurrently(int count, Request request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> request.send(index)));
            }
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Result createCard(String bearer, String key, Map<String, String> fields, byte[] image)
            throws IOException, InterruptedException {
        String boundary = "limite-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (image != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"carta.png\"\r\n"
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
            return Files.createTempDirectory("pokearena-cartas-");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Result(int status, String raw, JsonNode body) {
    }
}
