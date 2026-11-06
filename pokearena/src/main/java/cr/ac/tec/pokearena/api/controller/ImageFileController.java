package cr.ac.tec.pokearena.api.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import cr.ac.tec.pokearena.api.security.PublicRoute;
import cr.ac.tec.pokearena.application.port.ImageFile;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.domain.model.ImageFormat;

/**
 * Propósito: servir las imágenes guardadas por su URL pública (sección 6: "las imágenes se guardan
 * como URLs públicas relativas").
 * Responsabilidades: responder {@code GET /images/<cards|locations>/<id>/<archivo>} con el archivo y
 * su tipo de contenido, o 404 en el formato único si no existe.
 * Referencia: secciones 6 y 9.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Es un controlador y no un manejador de recursos estáticos de Spring, para que la ruta pase por
 *       la misma mecánica de autorización que el resto de la API: se declara {@link PublicRoute} y la
 *       prueba de cobertura de rutas la revisa.</li>
 *   <li>Es pública: el catálogo y la colección muestran las imágenes en etiquetas {@code <img>}, que
 *       no envían el encabezado {@code Authorization}.</li>
 *   <li>La ruta se valida en {@link ImageStorage#find}: solo se sirven archivos con la forma que genera
 *       el servidor, siempre dentro de la carpeta de imágenes.</li>
 *   <li>{@code X-Content-Type-Options: nosniff}: el navegador usa el tipo declarado y no intenta
 *       adivinar otro a partir del contenido.</li>
 *   <li>Caché del navegador por un año ({@code immutable}): el catálogo muestra hasta 10,000 cartas y
 *       cada imagen se descarga una sola vez. Las respuestas de error no llevan esa caché.</li>
 *   <li>El archivo se entrega en flujo ({@code FileSystemResource}), nunca como un arreglo de bytes.</li>
 * </ul>
 */
@RestController
@PublicRoute
public class ImageFileController {

    private static final Map<ImageFormat, MediaType> MEDIA_TYPES = Map.of(
            ImageFormat.PNG, MediaType.IMAGE_PNG,
            ImageFormat.JPEG, MediaType.IMAGE_JPEG,
            ImageFormat.WEBP, MediaType.parseMediaType("image/webp"));

    private static final String IMAGES_PATH = "/images/";
    private static final String NO_SNIFF_HEADER = "X-Content-Type-Options";
    private static final String NO_SNIFF = "nosniff";

    /**
     * Cuánto puede guardar el navegador una imagen: un año, el máximo habitual. Es seguro porque el
     * contenido de una URL nunca cambia: cada guardado usa una carpeta nueva con nombre UUID.
     */
    static final Duration IMAGE_CACHE_MAX_AGE = Duration.ofDays(365);

    /** "public, max-age=31536000, immutable": el navegador ni siquiera vuelve a preguntar si cambió. */
    static final String IMAGE_CACHE_CONTROL = "public, max-age=" + IMAGE_CACHE_MAX_AGE.toSeconds() + ", immutable";

    private final ImageStorage storage;

    public ImageFileController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/images/{folder}/{id}/{file}")
    public ResponseEntity<Resource> image(@PathVariable String folder, @PathVariable String id,
            @PathVariable String file) throws NoResourceFoundException {
        String relativePath = folder + "/" + id + "/" + file;
        // Misma excepción que una ruta inexistente: el manejador global responde 404 NOT_FOUND.
        ImageFile image = storage.find(relativePath).orElseThrow(
                () -> new NoResourceFoundException(HttpMethod.GET, IMAGES_PATH + relativePath, relativePath));
        // El cuerpo es el archivo como Resource: Spring lo copia a la respuesta en bloques, sin cargarlo
        // completo en memoria, e informa Content-Length y atiende solicitudes de rangos (Range).
        return ResponseEntity.ok()
                .contentType(MEDIA_TYPES.get(image.format()))
                .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
                .header(NO_SNIFF_HEADER, NO_SNIFF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(image.path()));
    }
}
