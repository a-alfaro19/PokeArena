package cr.ac.tec.pokearena.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cr.ac.tec.pokearena.api.web.CorrelationIdFilter;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.SampleDomainErrors;
import jakarta.servlet.Filter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar de punta a punta el formato único de errores y la correlación
 * (secciones 5.3 y 5.6), con el servidor real en un puerto aleatorio.
 * Responsabilidades: comprobar status, type, field, allowedValues y correlationId de cada
 * respuesta de error: los de dominio, los estándar de Spring, el límite del multipart, el error
 * inesperado y el error ocurrido en un filtro.
 * Referencia: secciones 5.3 y 5.6.
 *
 * <p>Se usa el servidor real y no MockMvc porque así se prueban también el límite del multipart
 * (lo aplica Tomcat) y el despacho a {@code /error}, que MockMvc no simula.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ ApiErrorHandlerIT.ErrorTestController.class, ApiErrorHandlerIT.FailingFilterConfiguration.class })
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Formato único de errores y correlación de solicitudes")
class ApiErrorHandlerIT extends PostgresIntegrationTestBase {

    private static final String BASE_PATH = "/test/errores";
    private static final String FAILING_FILTER_PATH = "/test/filtro-falla";
    private static final String SECRET = "detalle-interno-que-no-debe-salir";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Value("${app.images.max-file-size}")
    private DataSize maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

    // Envío muy superior al límite, para probar que Tomcat responde en lugar de cortar la conexión.
    private static final DataSize LARGE_UPLOAD = DataSize.ofMegabytes(12);

    // ---------------------------------------------------------------------------------------
    // Errores de dominio.
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorType.class)
    @DisplayName("Cada error de dominio responde con su status, type, field y allowedValues")
    void domainErrorsUseTheirStatus(ErrorType type) throws Exception {
        DomainException expected = SampleDomainErrors.of(type);

        Result result = send(get(BASE_PATH + "/dominio/" + type.name()));

        assertThat(result.status()).isEqualTo(expectedStatus(type));
        assertErrorBody(result, type.name(), expectedStatus(type));
        assertThat(result.body().get("detail").asString()).isEqualTo(expected.getMessage());
        assertThat(text(result.body(), "field")).isEqualTo(expected.field().orElse(null));
        assertThat(list(result.body(), "allowedValues")).isEqualTo(expected.allowedValues());
    }

    @Test
    @DisplayName("No autenticado (401) y sin permiso (403) son errores distintos")
    void notAuthenticatedAndForbiddenAreDistinct() throws Exception {
        Result notAuthenticated = send(get(BASE_PATH + "/dominio/NOT_AUTHENTICATED"));
        Result forbidden = send(get(BASE_PATH + "/dominio/FORBIDDEN"));

        assertThat(notAuthenticated.status()).isEqualTo(401);
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(notAuthenticated.body().get("type").asString())
                .isNotEqualTo(forbidden.body().get("type").asString());
    }

    @Test
    @DisplayName("Un campo opcional que no aplica no aparece en el JSON")
    void absentOptionalFieldsAreOmitted() throws Exception {
        Result result = send(get(BASE_PATH + "/dominio/FORBIDDEN"));

        assertThat(result.body().has("field")).isFalse();
        assertThat(result.body().has("allowedValues")).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // Excepciones estándar de Spring.
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Errores estándar de Spring con el mismo formato")
    class SpringErrors {

        @Test
        @DisplayName("Un parámetro obligatorio ausente es MISSING_FIELD con el nombre del campo")
        void missingParameter() throws Exception {
            Result result = send(get(BASE_PATH + "/parametros?kind=A"));

            assertErrorBody(result, "MISSING_FIELD", 400);
            assertThat(text(result.body(), "field")).isEqualTo("cost");
        }

        @Test
        @DisplayName("Un número con texto es INVALID_FIELD con el nombre del campo")
        void wrongNumberType() throws Exception {
            Result result = send(get(BASE_PATH + "/parametros?cost=abc&kind=A"));

            assertErrorBody(result, "INVALID_FIELD", 400);
            assertThat(text(result.body(), "field")).isEqualTo("cost");
        }

        @Test
        @DisplayName("Un valor fuera de un catálogo es INVALID_FIELD con los valores permitidos")
        void wrongEnumValue() throws Exception {
            Result result = send(get(BASE_PATH + "/parametros?cost=1&kind=Z"));

            assertErrorBody(result, "INVALID_FIELD", 400);
            assertThat(text(result.body(), "field")).isEqualTo("kind");
            assertThat(list(result.body(), "allowedValues")).containsExactly("A", "B");
        }

        @Test
        @DisplayName("Falta el encabezado Idempotency-Key: IDEMPOTENCY_KEY_REQUIRED")
        void missingIdempotencyKey() throws Exception {
            Result result = send(get(BASE_PATH + "/encabezado"));

            assertErrorBody(result, "IDEMPOTENCY_KEY_REQUIRED", 400);
            assertThat(text(result.body(), "field")).isEqualTo("Idempotency-Key");
        }

        @Test
        @DisplayName("Un cuerpo JSON mal formado es BAD_REQUEST")
        void unreadableBody() throws Exception {
            Result result = send(post(BASE_PATH + "/cuerpo", "application/json", "{ esto no es json"));

            assertErrorBody(result, "BAD_REQUEST", 400);
        }

        @Test
        @DisplayName("Un tipo de contenido no aceptado es UNSUPPORTED_MEDIA_TYPE")
        void unsupportedMediaType() throws Exception {
            Result result = send(post(BASE_PATH + "/cuerpo", "text/plain", "hola"));

            assertErrorBody(result, "UNSUPPORTED_MEDIA_TYPE", 415);
        }

        @Test
        @DisplayName("Una ruta inexistente es NOT_FOUND")
        void unknownRoute() throws Exception {
            Result result = send(get("/api/v1/no-existe"));

            assertErrorBody(result, "NOT_FOUND", 404);
        }

        @Test
        @DisplayName("Un método no permitido es METHOD_NOT_ALLOWED e indica los métodos válidos")
        void methodNotAllowed() throws Exception {
            Result result = send(request(BASE_PATH + "/parametros").DELETE().build());

            assertErrorBody(result, "METHOD_NOT_ALLOWED", 405);
            assertThat(result.response().headers().firstValue("Allow")).contains("GET");
        }

        @Test
        @DisplayName("Un archivo mayor al límite del multipart es IMAGE_TOO_LARGE")
        void fileOverMultipartLimit() throws Exception {
            Result result = send(multipart(BASE_PATH + "/archivo", (int) maxFileSize.toBytes() + 1));

            assertErrorBody(result, "IMAGE_TOO_LARGE", 400);
            assertThat(text(result.body(), "field")).isEqualTo("image");
        }

        @Test
        @DisplayName("Un archivo de unos 12 MB recibe el IMAGE_TOO_LARGE completo, no un corte de conexión")
        void muchLargerFileGetsCompleteError() throws Exception {
            // Mayor que max-request-size (6 MB) y que el descarte por defecto de Tomcat (2 MB): sin
            // server.tomcat.max-swallow-size, Tomcat cortaría la conexión y send() lanzaría IOException.
            assertThat(LARGE_UPLOAD.toBytes()).isGreaterThan(maxRequestSize.toBytes());
            String correlationId = "prueba-12mb-" + UUID.randomUUID();

            Result result = send(multipart(BASE_PATH + "/archivo", (int) LARGE_UPLOAD.toBytes(), correlationId));

            assertErrorBody(result, "IMAGE_TOO_LARGE", 400);
            assertThat(text(result.body(), "field")).isEqualTo("image");
            assertThat(result.body().get("correlationId").asString()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("Un archivo dentro del límite del multipart se acepta")
        void fileWithinMultipartLimit() throws Exception {
            Result result = send(multipart(BASE_PATH + "/archivo", (int) maxFileSize.toBytes() - 1024));

            assertThat(result.status()).isEqualTo(200);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Errores inesperados: nunca revelan el mensaje interno.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Un error inesperado es 500 INTERNAL_ERROR sin revelar el mensaje, y queda en el log")
    void unexpectedErrorDoesNotLeak(CapturedOutput output) throws Exception {
        String correlationId = "prueba-inesperado-" + UUID.randomUUID();

        Result result = send(request(BASE_PATH + "/inesperado").header(CorrelationIdFilter.HEADER, correlationId)
                .GET().build());

        assertErrorBody(result, "INTERNAL_ERROR", 500);
        assertThat(result.raw()).doesNotContain(SECRET).doesNotContain("IllegalStateException");
        assertThat(output.getOut()).contains("\"correlationId\":\"" + correlationId + "\"").contains(SECRET);
    }

    @Test
    @DisplayName("Un error en un filtro, fuera de los controladores, también usa el formato único")
    void errorOutsideControllerUsesSameFormat(CapturedOutput output) throws Exception {
        String correlationId = "prueba-filtro-" + UUID.randomUUID();

        Result result = send(request(FAILING_FILTER_PATH).header(CorrelationIdFilter.HEADER, correlationId)
                .GET().build());

        assertErrorBody(result, "INTERNAL_ERROR", 500);
        assertThat(result.body().get("correlationId").asString()).isEqualTo(correlationId);
        assertThat(result.raw()).doesNotContain(SECRET);
        assertThat(output.getOut()).contains("\"correlationId\":\"" + correlationId + "\"");
    }

    // ---------------------------------------------------------------------------------------
    // Correlación (sección 5.6).
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Correlación de solicitudes")
    class Correlation {

        @Test
        @DisplayName("Un X-Correlation-Id recibido se devuelve en el encabezado y en el cuerpo del error")
        void receivedIdIsEchoed() throws Exception {
            Result result = send(request(BASE_PATH + "/dominio/FORBIDDEN")
                    .header(CorrelationIdFilter.HEADER, "cliente-123").GET().build());

            assertThat(result.correlationHeader()).isEqualTo("cliente-123");
            assertThat(result.body().get("correlationId").asString()).isEqualTo("cliente-123");
        }

        @Test
        @DisplayName("Sin X-Correlation-Id se genera uno, igual en el encabezado y en el cuerpo")
        void missingIdIsGenerated() throws Exception {
            Result result = send(get(BASE_PATH + "/dominio/FORBIDDEN"));

            assertThat(result.correlationHeader()).matches(UUID_PATTERN);
            assertThat(result.body().get("correlationId").asString()).isEqualTo(result.correlationHeader());
        }

        @Test
        @DisplayName("Un X-Correlation-Id con caracteres inseguros se reemplaza por uno generado")
        void unsafeIdIsReplaced() throws Exception {
            Result result = send(request(BASE_PATH + "/dominio/FORBIDDEN")
                    .header(CorrelationIdFilter.HEADER, "abc def<script>").GET().build());

            assertThat(result.correlationHeader()).matches(UUID_PATTERN);
        }

        @Test
        @DisplayName("Las respuestas exitosas también llevan X-Correlation-Id")
        void successfulResponsesCarryId() throws Exception {
            Result result = send(get(BASE_PATH + "/parametros?cost=1&kind=A"));

            assertThat(result.status()).isEqualTo(200);
            assertThat(result.correlationHeader()).matches(UUID_PATTERN);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Controlador y filtro SOLO de pruebas. Están anidados en la clase de prueba, así Spring no los
    // encuentra al escanear: solo existen en este contexto, por el @Import de arriba.
    // ---------------------------------------------------------------------------------------

    /** Catálogo de prueba para verificar los valores permitidos de un enum. */
    enum SampleKind { A, B }

    /** Cuerpo JSON de prueba. */
    record SampleBody(String name) {
    }

    @RestController
    @RequestMapping(BASE_PATH)
    static class ErrorTestController {

        @GetMapping("/dominio/{type}")
        String domain(@PathVariable ErrorType type) {
            throw SampleDomainErrors.of(type);
        }

        @GetMapping("/parametros")
        String parameters(@RequestParam int cost, @RequestParam SampleKind kind) {
            return "ok";
        }

        @GetMapping("/encabezado")
        String header(@RequestHeader("Idempotency-Key") String key) {
            return "ok";
        }

        @PostMapping(path = "/cuerpo", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody SampleBody body) {
            return "ok";
        }

        @PostMapping(path = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String file(@RequestParam("image") MultipartFile image) {
            return "ok";
        }

        @GetMapping("/inesperado")
        String unexpected() {
            throw new IllegalStateException(SECRET);
        }
    }

    static class FailingFilterConfiguration {

        /** Filtro que falla antes de llegar a cualquier controlador. */
        @Bean
        FilterRegistrationBean<Filter> failingFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                throw new IllegalStateException(SECRET);
            });
            registration.addUrlPatterns(FAILING_FILTER_PATH);
            return registration;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ayudantes HTTP y de aserción.
    // ---------------------------------------------------------------------------------------

    /** Status esperado de cada tipo, tomado de la tabla de la sección 5.3 (independiente del código). */
    private static int expectedStatus(ErrorType type) {
        return switch (type) {
            case NOT_AUTHENTICATED, INVALID_IDENTITY_TOKEN -> 401;
            case FORBIDDEN -> 403;
            case IDENTITY_PROVIDER_UNAVAILABLE -> 503;
            case IDEMPOTENCY_KEY_REUSED -> 422;
            default -> 400;
        };
    }

    private static void assertErrorBody(Result result, String type, int status) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.response().headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertThat(result.body().get("type").asString()).isEqualTo(type);
        assertThat(result.body().get("status").asInt()).isEqualTo(status);
        assertThat(result.body().get("title").asString()).isNotBlank();
        assertThat(result.body().get("detail").asString()).isNotBlank();
        // El correlationId del cuerpo es el mismo del encabezado de la respuesta.
        assertThat(result.body().get("correlationId").asString()).isEqualTo(result.correlationHeader());
    }

    private static String text(JsonNode body, String name) {
        return body.has(name) ? body.get(name).asString() : null;
    }

    private static List<String> list(JsonNode body, String name) {
        List<String> values = new ArrayList<>();
        if (body.has(name)) {
            body.get(name).forEach(value -> values.add(value.asString()));
        }
        return values;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpRequest get(String path) {
        return request(path).GET().build();
    }

    private HttpRequest post(String path, String contentType, String body) {
        return request(path).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpRequest multipart(String path, int fileSize) throws IOException {
        return multipart(path, fileSize, null);
    }

    private HttpRequest multipart(String path, int fileSize, String correlationId) throws IOException {
        String boundary = "limite-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"imagen.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[fileSize]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (correlationId != null) {
            builder.header(CorrelationIdFilter.HEADER, correlationId);
        }
        return builder.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
    }

    private Result send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = response.body().isBlank() || !response.body().startsWith("{")
                ? json.createObjectNode() : json.readTree(response.body());
        return new Result(response, body);
    }

    private record Result(HttpResponse<String> response, JsonNode body) {
        int status() {
            return response.statusCode();
        }

        String raw() {
            return response.body();
        }

        String correlationHeader() {
            return response.headers().firstValue(CorrelationIdFilter.HEADER).orElse(null);
        }
    }
}
