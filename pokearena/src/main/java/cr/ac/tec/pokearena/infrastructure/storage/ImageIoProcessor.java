package cr.ac.tec.pokearena.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.error.DomainException;
import cr.ac.tec.pokearena.domain.error.ImageValidationException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.model.ImageFormat;
import cr.ac.tec.pokearena.domain.rule.ImageRules;
import net.coobird.thumbnailator.Thumbnails;

/**
 * Propósito: procesador de imágenes con ImageIO y Thumbnailator (corrección 6 del profesor).
 * Responsabilidades: validar una imagen en el orden de la especificación y generar su miniatura
 * con el tamaño exacto, en PNG.
 * Referencia: secciones 7.1 y 7.2, pasos 7 y 8.
 *
 * <p>Orden de la validación, del control más barato al más caro:
 * <ol>
 *   <li>(a) Peso en bytes, antes de decodificar nada: IMAGE_TOO_LARGE.</li>
 *   <li>(b) Formato real por los bytes iniciales ({@link ImageSignatures}): INVALID_IMAGE_FORMAT.</li>
 *   <li>(c) Decodificación con el lector de ese formato. Antes de decodificar se leen las
 *       dimensiones del encabezado y se rechaza una imagen con más píxeles que
 *       {@code app.images.max-pixels}: un archivo chico puede declarar dimensiones enormes y
 *       agotar la memoria al decodificarse. Si no se puede decodificar: INVALID_IMAGE_FORMAT.</li>
 *   <li>(d) Proporción: INVALID_IMAGE_RATIO.</li>
 *   <li>(e) Resolución mínima: IMAGE_RESOLUTION_TOO_LOW.</li>
 * </ol>
 *
 * <p>La miniatura se genera con {@code forceSize}: siempre mide exactamente lo que piden las reglas.
 * Como la proporción ya se validó con una tolerancia del 1 %, la deformación es como mucho del 1 %.
 */
@Component
public class ImageIoProcessor implements ImageProcessor {

    // Nombres de formato de ImageIO para cada formato permitido.
    private static final Map<ImageFormat, String> READER_FORMATS = Map.of(
            ImageFormat.PNG, "png",
            ImageFormat.JPEG, "jpeg",
            ImageFormat.WEBP, "webp");

    private static final String THUMBNAIL_FORMAT = "png";
    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;

    private final long maxPixels;

    public ImageIoProcessor(@Value("${app.images.max-pixels}") long maxPixels) {
        this.maxPixels = maxPixels;
        // En un jar de Spring Boot los plugins de ImageIO pueden no registrarse solos: se buscan aquí.
        ImageIO.scanForPlugins();
        // Sin archivos temporales: las imágenes se procesan en memoria (ya están acotadas en peso y píxeles).
        ImageIO.setUseCache(false);
        for (Map.Entry<ImageFormat, String> format : READER_FORMATS.entrySet()) {
            if (!ImageIO.getImageReadersByFormatName(format.getValue()).hasNext()) {
                throw new IllegalStateException("ImageIO no tiene lector para " + format.getKey()
                        + "; revise la dependencia imageio-webp de TwelveMonkeys");
            }
        }
    }

    @Override
    public ValidatedImage validate(byte[] content, ImageRules rules) {
        // (a) Peso, antes de decodificar.
        if (content != null && content.length > ImageRules.MAX_FILE_BYTES) {
            throw ImageValidationException.tooLarge("La imagen pesa más del máximo permitido de "
                    + ImageRules.MAX_FILE_BYTES / BYTES_PER_MEGABYTE + " MB.");
        }

        // (b) Formato real por los bytes iniciales.
        ImageFormat format = ImageSignatures.detect(content)
                .filter(ImageRules.ALLOWED_FORMATS::contains)
                .orElseThrow(() -> ImageValidationException.invalidFormat(
                        "El archivo no es una imagen PNG, JPG o WebP."));

        // (c) Decodificación, con el límite de píxeles.
        BufferedImage image = decode(content, format);
        int width = image.getWidth();
        int height = image.getHeight();

        // (d) Proporción.
        if (!rules.hasExpectedRatio(width, height)) {
            throw ImageValidationException.invalidRatio("La imagen debe tener proporción " + rules.ratioText()
                    + "; mide " + width + " × " + height + " píxeles.");
        }

        // (e) Resolución mínima.
        if (!rules.meetsMinimumResolution(width, height)) {
            throw ImageValidationException.resolutionTooLow("La imagen debe medir al menos " + rules.minWidth()
                    + " × " + rules.minHeight() + " píxeles; mide " + width + " × " + height + ".");
        }

        return new ValidatedImage(format, width, height, content);
    }

    @Override
    public byte[] generateThumbnail(ValidatedImage image, ImageRules rules) {
        try {
            BufferedImage source = decode(image.content(), image.format());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thumbnails.of(source)
                    .forceSize(rules.thumbnailWidth(), rules.thumbnailHeight())
                    .outputFormat(THUMBNAIL_FORMAT)
                    .toOutputStream(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException failure) {
            // El caso de uso decide: guarda la entidad no disponible y la tarea programada reintenta.
            throw new ThumbnailGenerationException("No se pudo generar la miniatura de "
                    + rules.thumbnailWidth() + " × " + rules.thumbnailHeight(), failure);
        }
    }

    /** Decodifica con el lector del formato detectado, rechazando imágenes con demasiados píxeles. */
    private BufferedImage decode(byte[] content, ImageFormat format) {
        ImageReader reader = readerFor(format);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            // Las dimensiones salen del encabezado, sin decodificar los píxeles todavía.
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if ((long) width * height > maxPixels) {
                // Mismo tipo que el exceso de peso (es una imagen "demasiado grande"), pero con otro
                // mensaje: aquí el problema son las dimensiones, no los bytes.
                throw ImageValidationException.tooLarge("Las dimensiones de la imagen (" + width + " × " + height
                        + " píxeles) superan el máximo permitido de " + maxPixels + " píxeles.");
            }
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw invalidContent();
            }
            return image;
        } catch (DomainException error) {
            throw error;
        } catch (IOException | RuntimeException unreadable) {
            // Firma correcta pero contenido dañado o truncado: no es una imagen válida de ese formato.
            throw invalidContent();
        } finally {
            reader.dispose();
        }
    }

    private static ImageReader readerFor(ImageFormat format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(READER_FORMATS.get(format));
        if (!readers.hasNext()) {
            throw new IllegalStateException("No hay lector de ImageIO para " + format);
        }
        return readers.next();
    }

    private static ImageValidationException invalidContent() {
        return ImageValidationException.invalidFormat("El archivo está dañado o no es una imagen PNG, JPG o WebP válida.");
    }
}
