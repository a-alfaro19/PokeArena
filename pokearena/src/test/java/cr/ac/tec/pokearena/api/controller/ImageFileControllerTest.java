package cr.ac.tec.pokearena.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import cr.ac.tec.pokearena.application.port.ImageFile;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: pruebas unitarias de la respuesta que arma {@link ImageFileController}.
 * Responsabilidades: comprobar que el archivo se entrega en flujo (un {@code Resource} que apunta al
 * archivo, nunca sus bytes ya cargados), con la caché inmutable y el tipo de contenido correcto, y
 * que una imagen inexistente termina en la misma excepción que una ruta inexistente.
 * Referencia: secciones 6 y 9.
 */
@DisplayName("Respuesta del controlador de imágenes")
class ImageFileControllerTest {

    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @TempDir
    Path folder;

    @Test
    @DisplayName("Entrega el archivo en flujo: el cuerpo es un Resource del archivo, no un arreglo de bytes")
    void bodyIsStreamedFromFile() throws IOException, NoResourceFoundException {
        Path file = Files.write(folder.resolve("large.png"), new byte[] { 1, 2, 3 });
        ImageFileController controller = new ImageFileController(storageWith(new ImageFile(file, ImageFormat.PNG)));

        ResponseEntity<Resource> response = controller.image("cards", UUID, "large.png");

        assertThat(response.getBody()).isInstanceOf(FileSystemResource.class);
        assertThat(((FileSystemResource) response.getBody()).getFile().toPath()).isEqualTo(file);
    }

    @Test
    @DisplayName("Responde con Cache-Control \"public, max-age=31536000, immutable\" y el tipo del formato")
    void cachesImmutably() throws IOException, NoResourceFoundException {
        Path file = Files.write(folder.resolve("large.webp"), new byte[] { 1 });
        ImageFileController controller = new ImageFileController(storageWith(new ImageFile(file, ImageFormat.WEBP)));

        ResponseEntity<Resource> response = controller.image("cards", UUID, "large.webp");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=31536000, immutable");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/webp"));
    }

    @Test
    @DisplayName("Una imagen que no existe lanza NoResourceFoundException, la misma de una ruta inexistente")
    void missingImageIsNoResource() {
        ImageFileController controller = new ImageFileController(storageWith(null));

        assertThatThrownBy(() -> controller.image("cards", UUID, "large.png"))
                .isInstanceOf(NoResourceFoundException.class);
    }

    /** Almacenamiento de prueba que solo sabe encontrar un archivo. */
    private static ImageStorage storageWith(ImageFile file) {
        return new ImageStorage() {
            @Override
            public StoredImages save(ImageRules rules, ValidatedImage large, byte[] thumbnail) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String storageKey) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ImageFile> find(String relativePath) {
                return Optional.ofNullable(file);
            }

            @Override
            public Optional<byte[]> readLarge(String largeUrl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String saveThumbnail(String largeUrl, byte[] thumbnail) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
