package cr.ac.tec.pokearena.infrastructure.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.port.ImageFile;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.StoredImages;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: almacenamiento de imágenes en el sistema de archivos (sección 9; un volumen compartido
 * por las 2 instancias de la API).
 * Responsabilidades: guardar la versión grande (en su formato original) y la miniatura (PNG) en una
 * carpeta nueva por entidad, devolver sus URLs públicas relativas, borrarlas como compensación y
 * encontrarlas para servirlas.
 * Referencia: secciones 6, 7.1, 7.2 (paso 9) y 9.
 *
 * <p>Organización: {@code <app.images.root-dir>/<cards|locations>/<uuid>/large.<ext>} y
 * {@code .../small.png}. La URL es la misma ruta bajo {@code /images/}, por ejemplo
 * {@code /images/cards/<uuid>/large.png}, y la clave de borrado es {@code cards/<uuid>}.
 *
 * <p>Seguridad: ningún nombre viene del cliente. La carpeta es un UUID generado aquí y los nombres de
 * archivo son fijos. Además, toda ruta que llega desde afuera (una clave o una URL) se valida contra
 * la forma exacta que genera el servidor y se comprueba que, ya resuelta, quede dentro de la carpeta
 * raíz: un {@code ../} no puede salir de ella.
 */
@Component
public class FileSystemImageStorage implements ImageStorage {

    /** Prefijo de las URLs públicas de las imágenes. */
    public static final String URL_PREFIX = "/images/";

    private static final Map<ImageRules.Kind, String> FOLDERS = Map.of(
            ImageRules.Kind.CARD, "cards",
            ImageRules.Kind.LOCATION, "locations");

    private static final Map<ImageFormat, String> EXTENSIONS = Map.of(
            ImageFormat.PNG, "png",
            ImageFormat.JPEG, "jpg",
            ImageFormat.WEBP, "webp");

    private static final String LARGE_NAME = "large";
    private static final String SMALL_FILE = "small.png";

    // Única forma válida de una clave y de una ruta pública: carpeta conocida, UUID y nombre fijo.
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String FOLDER_PATTERN = "(cards|locations)";
    private static final Pattern KEY = Pattern.compile(FOLDER_PATTERN + "/" + UUID_PATTERN);
    private static final Pattern FILE = Pattern.compile(
            "(" + FOLDER_PATTERN + "/" + UUID_PATTERN + ")/(large\\.(png|jpg|webp)|small\\.png)");

    private final Path root;

    public FileSystemImageStorage(@Value("${app.images.root-dir}") String rootDir) {
        // La carpeta se crea al guardar la primera imagen, no al arrancar.
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredImages save(ImageRules rules, ValidatedImage large, byte[] thumbnail) {
        String key = FOLDERS.get(rules.kind()) + "/" + UUID.randomUUID();
        Path folder = inside(key);
        String largeFile = LARGE_NAME + "." + EXTENSIONS.get(large.format());
        try {
            Files.createDirectories(folder);
            // CREATE_NEW: nunca reemplaza un archivo existente.
            Files.write(folder.resolve(largeFile), large.content(), StandardOpenOption.CREATE_NEW);
            if (thumbnail != null) {
                Files.write(folder.resolve(SMALL_FILE), thumbnail, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException failure) {
            // No queda una carpeta a medias: se borra lo que alcanzó a escribirse.
            deleteQuietly(folder);
            throw new UncheckedIOException("No se pudieron guardar las imágenes en " + folder, failure);
        }
        return new StoredImages(key, URL_PREFIX + key + "/" + largeFile,
                thumbnail == null ? null : URL_PREFIX + key + "/" + SMALL_FILE);
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || !KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Clave de almacenamiento inválida: " + storageKey);
        }
        Path folder = inside(storageKey);
        if (Files.exists(folder)) {
            try {
                deleteRecursively(folder);
            } catch (IOException failure) {
                throw new UncheckedIOException("No se pudieron borrar las imágenes de " + folder, failure);
            }
        }
    }

    @Override
    public Optional<ImageFile> find(String relativePath) {
        if (relativePath == null) {
            return Optional.empty();
        }
        Matcher match = FILE.matcher(relativePath);
        if (!match.matches()) {
            return Optional.empty();
        }
        Path file = inside(relativePath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(new ImageFile(file, formatOf(match.group(3))));
    }

    /**
     * Resuelve {@code relative} dentro de la carpeta raíz y comprueba que no salga de ella. Las
     * expresiones regulares ya impiden {@code ..}; esta es la segunda barrera.
     */
    private Path inside(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("La ruta sale de la carpeta de imágenes: " + relative);
        }
        return resolved;
    }

    private static ImageFormat formatOf(String fileName) {
        return EXTENSIONS.entrySet().stream()
                .filter(entry -> fileName.endsWith("." + entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private static void deleteRecursively(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void deleteQuietly(Path folder) {
        try {
            if (Files.exists(folder)) {
                deleteRecursively(folder);
            }
        } catch (IOException ignored) {
            // Ya se está informando la falla original; un resto lo limpiará la tarea de huérfanos (ADR-002).
        }
    }
}
