package cr.ac.tec.pokearena.application.port;

import java.util.Objects;

import cr.ac.tec.pokearena.domain.model.ImageFormat;

/**
 * Propósito: imagen que ya cumplió todos los requisitos (pasos 7 de los diagramas A.1 y A.2).
 * Responsabilidades: llevar el formato detectado por el contenido, las dimensiones reales y los
 * bytes originales, que se guardan sin cambios como versión grande.
 * Referencia: secciones 7.1 y 7.2.
 *
 * @param format  formato real, detectado por los bytes iniciales
 * @param width   ancho en píxeles
 * @param height  alto en píxeles
 * @param content bytes originales de la imagen
 */
public record ValidatedImage(ImageFormat format, int width, int height, byte[] content) {

    public ValidatedImage {
        Objects.requireNonNull(format, "El formato es obligatorio");
        Objects.requireNonNull(content, "El contenido es obligatorio");
    }
}
