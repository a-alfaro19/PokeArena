package cr.ac.tec.pokearena.domain.rule;

import java.util.Objects;
import java.util.Set;

import cr.ac.tec.pokearena.domain.model.ImageFormat;

/**
 * Propósito: requisitos de las imágenes de cartas y de ubicaciones (secciones 7.1 y 7.2).
 * Responsabilidades: reunir en un solo valor la proporción, la resolución mínima y el tamaño de la
 * miniatura de cada tipo de imagen, más los requisitos que comparten (formatos, peso y tolerancia),
 * y decidir si unas dimensiones los cumplen.
 * Referencia: criterios C.1 y C.2; pasos 7 y 8 de los diagramas A.1 y A.2.
 *
 * @param kind            tipo de imagen; también decide su carpeta de almacenamiento
 * @param ratioWidth      parte del ancho de la proporción (5 en 5:7)
 * @param ratioHeight     parte del alto de la proporción (7 en 5:7)
 * @param minWidth        ancho mínimo en píxeles
 * @param minHeight       alto mínimo en píxeles
 * @param thumbnailWidth  ancho EXACTO de la miniatura
 * @param thumbnailHeight alto EXACTO de la miniatura
 */
public record ImageRules(Kind kind, int ratioWidth, int ratioHeight, int minWidth, int minHeight,
        int thumbnailWidth, int thumbnailHeight) {

    /** Tipo de imagen. */
    public enum Kind {
        CARD,
        LOCATION
    }

    // ---- Requisitos compartidos (C.1 y C.2) ----

    /** Formatos permitidos, detectados por el contenido. */
    public static final Set<ImageFormat> ALLOWED_FORMATS = Set.of(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.WEBP);

    private static final int BYTES_PER_MEGABYTE = 1024 * 1024;
    private static final int MAX_FILE_MEGABYTES = 5;

    /** Peso máximo: 5 MB. Debe coincidir con {@code app.images.max-file-size} (una prueba lo comprueba). */
    public static final long MAX_FILE_BYTES = (long) MAX_FILE_MEGABYTES * BYTES_PER_MEGABYTE;

    /** Tolerancia de la proporción: 1 %. */
    public static final double RATIO_TOLERANCE = 0.01;

    // ---- Carta (C.1): vertical 5:7, mínimo 600 x 840, miniatura 200 x 280 ----

    private static final int CARD_RATIO_WIDTH = 5;
    private static final int CARD_RATIO_HEIGHT = 7;
    private static final int CARD_MIN_WIDTH = 600;
    private static final int CARD_MIN_HEIGHT = 840;
    private static final int CARD_THUMBNAIL_WIDTH = 200;
    private static final int CARD_THUMBNAIL_HEIGHT = 280;

    public static final ImageRules CARD = new ImageRules(Kind.CARD, CARD_RATIO_WIDTH, CARD_RATIO_HEIGHT,
            CARD_MIN_WIDTH, CARD_MIN_HEIGHT, CARD_THUMBNAIL_WIDTH, CARD_THUMBNAIL_HEIGHT);

    // ---- Ubicación (C.2): horizontal 4:3, mínimo 800 x 600, miniatura 320 x 240 ----

    private static final int LOCATION_RATIO_WIDTH = 4;
    private static final int LOCATION_RATIO_HEIGHT = 3;
    private static final int LOCATION_MIN_WIDTH = 800;
    private static final int LOCATION_MIN_HEIGHT = 600;
    private static final int LOCATION_THUMBNAIL_WIDTH = 320;
    private static final int LOCATION_THUMBNAIL_HEIGHT = 240;

    public static final ImageRules LOCATION = new ImageRules(Kind.LOCATION, LOCATION_RATIO_WIDTH,
            LOCATION_RATIO_HEIGHT, LOCATION_MIN_WIDTH, LOCATION_MIN_HEIGHT, LOCATION_THUMBNAIL_WIDTH,
            LOCATION_THUMBNAIL_HEIGHT);

    public ImageRules {
        Objects.requireNonNull(kind, "El tipo de imagen es obligatorio");
    }

    /**
     * La proporción está dentro de la tolerancia. Se compara la proporción real con la esperada
     * como diferencia relativa: |(ancho / alto) / (rw / rh) - 1| <= 1 %.
     */
    public boolean hasExpectedRatio(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        double actual = (double) width / height;
        double expected = (double) ratioWidth / ratioHeight;
        return Math.abs(actual / expected - 1) <= RATIO_TOLERANCE;
    }

    /** Las dimensiones alcanzan la resolución mínima en ancho y en alto. */
    public boolean meetsMinimumResolution(int width, int height) {
        return width >= minWidth && height >= minHeight;
    }

    /** Proporción en texto, por ejemplo "5:7", para los mensajes de error. */
    public String ratioText() {
        return ratioWidth + ":" + ratioHeight;
    }
}
