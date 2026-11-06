package cr.ac.tec.pokearena.infrastructure.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.ImageFormat;

/**
 * Propósito: detectar el formato REAL de una imagen por sus bytes iniciales ("firma" del archivo).
 * Responsabilidades: reconocer PNG, JPEG y WebP sin mirar la extensión ni el tipo que declara el
 * cliente, que se pueden falsificar (un archivo de texto llamado {@code foto.png}).
 * Referencia: secciones 7.1 y 7.2, paso 7 ("formato real detectado por contenido").
 *
 * <p>Firmas (especificaciones de cada formato):
 * <ul>
 *   <li>PNG: los 8 bytes {@code 89 50 4E 47 0D 0A 1A 0A}.</li>
 *   <li>JPEG: los 3 bytes {@code FF D8 FF}.</li>
 *   <li>WebP: contenedor RIFF: {@code "RIFF"}, 4 bytes de tamaño y {@code "WEBP"}.</li>
 * </ul>
 */
final class ImageSignatures {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    // En WebP, "WEBP" va después de "RIFF" (4 bytes) y del tamaño del contenedor (4 bytes).
    private static final int WEBP_MARKER_OFFSET = RIFF.length + Integer.BYTES;

    private ImageSignatures() {
    }

    /** Formato de {@code content}, o vacío si no es ninguno de los permitidos. */
    static Optional<ImageFormat> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0, PNG)) {
            return Optional.of(ImageFormat.PNG);
        }
        if (startsWith(content, 0, JPEG)) {
            return Optional.of(ImageFormat.JPEG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, WEBP_MARKER_OFFSET, WEBP)) {
            return Optional.of(ImageFormat.WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        return content.length >= offset + signature.length
                && Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
    }
}
