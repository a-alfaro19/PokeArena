package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.unit.DataSize;

import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestImages;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: verificar con el servidor real que las imágenes guardadas se sirven por su URL pública
 * (secciones 6 y 9) y que el procesador y el almacenamiento quedan conectados como componentes.
 * Responsabilidades: comprobar que la URL es pública (sin token), el tipo de contenido de cada
 * formato, el 404 en el formato único, que una ruta maliciosa no sirve nada y que el límite del
 * multipart coincide con el peso máximo del dominio.
 * Referencia: corrección 6 del profesor; secciones 6, 7.1, 7.2 y 9.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Servir las imágenes guardadas")
class ImageFileControllerIT extends PostgresIntegrationTestBase {

    // Carpeta raíz solo de esta prueba; afuera queda un archivo que nunca debe servirse.
    private static final Path WORKSPACE = createWorkspace();
    private static final Path ROOT = WORKSPACE.resolve("images");
    private static final String SECRET_CONTENT = "contenido-secreto-fuera-de-la-carpeta";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ImageProcessor processor;

    @Autowired
    private ImageStorage storage;

    @Value("${app.images.max-file-size}")
    private DataSize multipartLimit;

    @DynamicPropertySource
    static void imagesRoot(DynamicPropertyRegistry registry) {
        registry.add("app.images.root-dir", ROOT::toString);
    }

    @AfterAll
    static void removeWorkspace() throws IOException {
        try (Stream<Path> files = Files.walk(WORKSPACE)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @DisplayName("Una imagen guardada se sirve por su URL sin credenciales, con su tipo y sin adivinar el contenido")
    void storedImageIsServedPublicly() throws Exception {
        StoredImages stored = validateAndStore(TestImages.png(600, 840), ImageRules.CARD);

        HttpResponse<byte[]> large = get(stored.largeUrl());
        HttpResponse<byte[]> small = get(stored.smallUrl());

        assertThat(large.statusCode()).isEqualTo(200);
        assertThat(large.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(large.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(large.body()).isEqualTo(TestImages.png(600, 840));
        // La miniatura servida mide exactamente 200 × 280.
        assertThat(TestImages.read(small.body()).getWidth()).isEqualTo(200);
        assertThat(TestImages.read(small.body()).getHeight()).isEqualTo(280);
    }

    @Test
    @DisplayName("Una carta WebP se sirve como image/webp y una ubicación JPG como image/jpeg")
    void contentTypeFollowsFormat() throws Exception {
        StoredImages webp = validateAndStore(TestImages.webpCard(), ImageRules.CARD);
        StoredImages jpeg = validateAndStore(TestImages.jpeg(800, 600), ImageRules.LOCATION);

        assertThat(get(webp.largeUrl()).headers().firstValue("Content-Type")).contains("image/webp");
        assertThat(get(jpeg.largeUrl()).headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(jpeg.largeUrl()).startsWith("/images/locations/");
    }

    @Test
    @DisplayName("Una imagen se cachea un año como inmutable: su contenido nunca cambia")
    void imageIsCachedImmutably() throws Exception {
        StoredImages stored = validateAndStore(TestImages.png(600, 840), ImageRules.CARD);

        HttpResponse<byte[]> response = get(stored.largeUrl());

        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("El archivo se entrega en flujo: informa Content-Length y atiende una lectura por rangos")
    void imageIsStreamed() throws Exception {
        byte[] content = TestImages.png(600, 840);
        StoredImages stored = validateAndStore(content, ImageRules.CARD);

        HttpResponse<byte[]> whole = get(stored.largeUrl());
        HttpResponse<byte[]> firstBytes = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + stored.largeUrl())).header("Range", "bytes=0-9").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(whole.headers().firstValueAsLong("Content-Length")).hasValue(content.length);
        // 206: Spring sirvió solo un tramo del archivo, posible porque lo entrega como recurso en flujo.
        assertThat(firstBytes.statusCode()).isEqualTo(206);
        assertThat(firstBytes.body()).isEqualTo(java.util.Arrays.copyOf(content, 10));
    }

    @Test
    @DisplayName("Una imagen que no existe responde 404 NOT_FOUND en el formato único, sin caché inmutable")
    void missingImageIsNotFound() throws Exception {
        HttpResponse<byte[]> response = get("/images/cards/00000000-0000-0000-0000-000000000000/large.png");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JsonMapper.builder().build().readTree(response.body()).get("type").asString())
                .isEqualTo("NOT_FOUND");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).doesNotContain("immutable");
    }

    @Test
    @DisplayName("Una ruta que intenta salir de la carpeta de imágenes no sirve el archivo de afuera")
    void traversalIsNotServed() throws Exception {
        for (String path : new String[] { "/images/../secreto.txt", "/images/cards/..%2F..%2Fsecreto.txt/x/y",
                "/images/cards/%2E%2E/%2E%2E/secreto.txt" }) {
            HttpResponse<byte[]> response = get(path);

            assertThat(response.statusCode()).as(path).isNotEqualTo(200);
            assertThat(new String(response.body())).as(path).doesNotContain(SECRET_CONTENT);
        }
    }

    @Test
    @DisplayName("El límite del multipart (app.images.max-file-size) coincide con el máximo de 5 MB del dominio")
    void multipartLimitMatchesDomain() {
        assertThat(multipartLimit.toBytes()).isEqualTo(ImageRules.MAX_FILE_BYTES);
    }

    private StoredImages validateAndStore(byte[] content, ImageRules rules) {
        ValidatedImage image = processor.validate(content, rules);
        return storage.save(rules, image, processor.generateThumbnail(image, rules));
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Path createWorkspace() {
        try {
            Path workspace = Files.createTempDirectory("pokearena-imagenes-");
            Files.writeString(workspace.resolve("secreto.txt"), SECRET_CONTENT);
            return workspace;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
