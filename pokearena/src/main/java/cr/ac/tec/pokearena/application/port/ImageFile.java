package cr.ac.tec.pokearena.application.port;

import java.nio.file.Path;

import cr.ac.tec.pokearena.domain.model.ImageFormat;

/**
 * Propósito: archivo de imagen guardado, listo para servirse por su URL pública.
 * Responsabilidades: indicar dónde está el archivo y en qué formato, para responder con el tipo de
 * contenido correcto.
 * Referencia: sección 9 (imágenes en el sistema de archivos, en un volumen compartido).
 *
 * @param path   ubicación del archivo, siempre dentro de la carpeta raíz de imágenes
 * @param format formato del archivo
 */
public record ImageFile(Path path, ImageFormat format) {
}
