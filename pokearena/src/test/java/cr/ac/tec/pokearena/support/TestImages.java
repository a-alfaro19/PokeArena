package cr.ac.tec.pokearena.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Propósito: imágenes para las pruebas, generadas por código con las medidas exactas (sección 14).
 * Responsabilidades: crear PNG, JPG y GIF del tamaño pedido y cargar el único archivo WebP del
 * repositorio ({@code src/test/resources/images/carta-600x840.webp}). Ese archivo existe porque
 * ImageIO, incluso con TwelveMonkeys, puede leer WebP pero no escribirlo.
 * Referencia: sección 14 ("las imágenes de las pruebas se generan por código").
 */
public final class TestImages {

    /** WebP de 600 x 840 (carta 5:7), en src/test/resources. */
    public static final String WEBP_CARD_RESOURCE = "/images/carta-600x840.webp";
    public static final int WEBP_CARD_WIDTH = 600;
    public static final int WEBP_CARD_HEIGHT = 840;

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(drawing(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        // JPEG no tiene transparencia: la imagen debe ser RGB.
        return encode(drawing(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    /** GIF: una imagen real, pero de un formato que no está permitido. */
    public static byte[] gif(int width, int height) {
        return encode(drawing(width, height, BufferedImage.TYPE_INT_RGB), "gif");
    }

    public static byte[] webpCard() {
        try (InputStream input = TestImages.class.getResourceAsStream(WEBP_CARD_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Falta el recurso de prueba " + WEBP_CARD_RESOURCE);
            }
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Dimensiones de una imagen ya codificada, leídas de vuelta con ImageIO. */
    public static BufferedImage read(byte[] content) {
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static BufferedImage drawing(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("ImageIO no puede escribir " + format);
            }
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
