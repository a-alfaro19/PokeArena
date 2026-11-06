package cr.ac.tec.pokearena.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import cr.ac.tec.pokearena.support.TestImages;

/**
 * Propósito: pruebas del almacenamiento de imágenes en el sistema de archivos (paso 9 de A.1 y A.2).
 * Responsabilidades: comprobar dónde y con qué nombres se guardan las imágenes, sus URLs públicas,
 * el borrado como compensación y que ninguna ruta recibida desde afuera sale de la carpeta raíz.
 * Referencia: secciones 6, 7.1, 7.2 y 9.
 */
@DisplayName("Almacenamiento de imágenes en el sistema de archivos")
class FileSystemImageStorageTest {

    private static final byte[] THUMBNAIL = TestImages.png(200, 280);

    @TempDir
    Path workspace;

    private Path root;
    private Path outsideSecret;
    private FileSystemImageStorage storage;

    @BeforeEach
    void prepare() throws IOException {
        // La raíz es una subcarpeta; afuera queda un archivo que ninguna operación debe tocar ni servir.
        root = workspace.resolve("images");
        outsideSecret = Files.writeString(workspace.resolve("secreto.txt"), "no debe salir");
        storage = new FileSystemImageStorage(root.toString());
    }

    @Test
    @DisplayName("Guarda la versión grande y la miniatura de una carta y devuelve sus URLs relativas")
    void savesCardImages() {
        ValidatedImage large = image(ImageFormat.PNG, TestImages.png(600, 840));

        StoredImages stored = storage.save(ImageRules.CARD, large, THUMBNAIL);

        assertThat(stored.storageKey()).matches("cards/[0-9a-f-]{36}");
        assertThat(stored.largeUrl()).isEqualTo("/images/" + stored.storageKey() + "/large.png");
        assertThat(stored.smallUrl()).isEqualTo("/images/" + stored.storageKey() + "/small.png");
        assertThat(root.resolve(stored.storageKey()).resolve("large.png")).hasBinaryContent(large.content());
        assertThat(root.resolve(stored.storageKey()).resolve("small.png")).hasBinaryContent(THUMBNAIL);
    }

    @Test
    @DisplayName("La versión grande conserva su formato original: JPG queda .jpg y WebP queda .webp")
    void largeKeepsOriginalFormat() {
        StoredImages jpeg = storage.save(ImageRules.CARD, image(ImageFormat.JPEG, TestImages.jpeg(600, 840)), THUMBNAIL);
        StoredImages webp = storage.save(ImageRules.CARD, image(ImageFormat.WEBP, TestImages.webpCard()), THUMBNAIL);

        assertThat(jpeg.largeUrl()).endsWith("/large.jpg");
        assertThat(webp.largeUrl()).endsWith("/large.webp");
        assertThat(root.resolve(webp.storageKey()).resolve("large.webp")).hasBinaryContent(TestImages.webpCard());
    }

    @Test
    @DisplayName("Las imágenes de ubicaciones van en su propia carpeta")
    void locationsHaveTheirOwnFolder() {
        StoredImages stored = storage.save(ImageRules.LOCATION, image(ImageFormat.PNG, TestImages.png(800, 600)),
                TestImages.png(320, 240));

        assertThat(stored.storageKey()).startsWith("locations/");
        assertThat(stored.largeUrl()).startsWith("/images/locations/");
    }

    @Test
    @DisplayName("Si la miniatura falló, guarda solo la versión grande y no informa URL pequeña")
    void savesWithoutThumbnail() {
        StoredImages stored = storage.save(ImageRules.CARD, image(ImageFormat.PNG, TestImages.png(600, 840)), null);

        assertThat(stored.smallUrl()).isNull();
        assertThat(root.resolve(stored.storageKey()).resolve("small.png")).doesNotExist();
    }

    @Test
    @DisplayName("Los nombres los genera el servidor: cada guardado usa una carpeta nueva y nombres fijos")
    void namesAreGeneratedByServer() throws IOException {
        StoredImages first = storage.save(ImageRules.CARD, image(ImageFormat.PNG, TestImages.png(600, 840)), THUMBNAIL);
        StoredImages second = storage.save(ImageRules.CARD, image(ImageFormat.PNG, TestImages.png(600, 840)), THUMBNAIL);

        assertThat(second.storageKey()).isNotEqualTo(first.storageKey());
        try (var files = Files.list(root.resolve(first.storageKey()))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactlyInAnyOrder("large.png",
                    "small.png");
        }
    }

    @Test
    @DisplayName("Borrar elimina lo guardado, y borrar dos veces no falla")
    void deletesStoredImages() {
        StoredImages stored = storage.save(ImageRules.CARD, image(ImageFormat.PNG, TestImages.png(600, 840)), THUMBNAIL);

        storage.delete(stored.storageKey());
        storage.delete(stored.storageKey());

        assertThat(root.resolve(stored.storageKey())).doesNotExist();
        assertThat(storage.find(stored.storageKey() + "/large.png")).isEmpty();
    }

    @Test
    @DisplayName("Encuentra un archivo guardado por su URL, con su formato")
    void findsStoredFile() {
        StoredImages stored = storage.save(ImageRules.CARD, image(ImageFormat.WEBP, TestImages.webpCard()), THUMBNAIL);
        String relative = stored.largeUrl().substring(FileSystemImageStorage.URL_PREFIX.length());

        assertThat(storage.find(relative)).hasValueSatisfying(file -> {
            assertThat(file.format()).isEqualTo(ImageFormat.WEBP);
            assertThat(file.path()).startsWith(root);
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "../secreto.txt", "cards/../../secreto.txt", "cards/..%2F..%2Fsecreto.txt",
            "/etc/passwd", "C:\\Windows\\win.ini", "cards/00000000-0000-0000-0000-000000000000/../../../secreto.txt",
            "cards/00000000-0000-0000-0000-000000000000/otro.png", "otra/00000000-0000-0000-0000-000000000000/large.png" })
    @DisplayName("Una ruta maliciosa o con otra forma no se sirve y no sale de la carpeta")
    void maliciousPathIsNotServed(String path) {
        assertThat(storage.find(path)).isEmpty();
        assertThat(outsideSecret).hasContent("no debe salir");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "..", "../", "cards/..", "cards/../..", "/tmp", "cards/no-es-un-uuid", "cards" })
    @DisplayName("Una clave de borrado maliciosa se rechaza y no borra nada fuera de la carpeta")
    void maliciousKeyIsRejected(String key) {
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
        assertThat(outsideSecret).exists().hasContent("no debe salir");
    }

    @Test
    @DisplayName("Reintentos: lee la versión grande guardada por su URL")
    void readsLargeImage() {
        byte[] content = TestImages.png(600, 840);
        StoredImages stored = storage.save(ImageRules.CARD, image(ImageFormat.PNG, content), null);

        assertThat(storage.readLarge(stored.largeUrl())).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(content));
    }

    @Test
    @DisplayName("Reintentos: si la versión grande ya no existe, o la URL no es de una versión grande, no hay nada que leer")
    void readLargeMissingOrInvalid() {
        StoredImages stored = storage.save(ImageRules.CARD, image(ImageFormat.PNG, TestImages.png(600, 840)), THUMBNAIL);
        storage.delete(stored.storageKey());

        assertThat(storage.readLarge(stored.largeUrl())).isEmpty();
        assertThat(storage.readLarge(stored.smallUrl())).isEmpty();
        assertThat(storage.readLarge("/images/../secreto.txt")).isEmpty();
        assertThat(storage.readLarge(null)).isEmpty();
    }

    @Test
    @DisplayName("Reintentos: guarda solo la miniatura en la carpeta de la entidad y devuelve su URL")
    void savesOnlyThumbnail() throws IOException {
        StoredImages stored = storage.save(ImageRules.LOCATION, image(ImageFormat.PNG, TestImages.png(800, 600)), null);

        String smallUrl = storage.saveThumbnail(stored.largeUrl(), THUMBNAIL);

        assertThat(smallUrl).isEqualTo(FileSystemImageStorage.URL_PREFIX + stored.storageKey() + "/small.png");
        assertThat(Files.readAllBytes(root.resolve(stored.storageKey()).resolve("small.png"))).isEqualTo(THUMBNAIL);
    }

    @Test
    @DisplayName("Reintentos: guardar la miniatura con una URL que no es de una versión grande se rechaza")
    void saveThumbnailRejectsInvalidUrl() {
        assertThatThrownBy(() -> storage.saveThumbnail("/images/../../secreto/large.png", THUMBNAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ValidatedImage image(ImageFormat format, byte[] content) {
        return new ValidatedImage(format, 0, 0, content);
    }
}
