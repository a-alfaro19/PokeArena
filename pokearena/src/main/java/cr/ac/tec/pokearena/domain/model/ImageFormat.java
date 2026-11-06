package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: formatos de imagen que acepta PokéArena (secciones 7.1 y 7.2).
 * Responsabilidades: nombrar los tres formatos permitidos. El formato de una imagen se decide por
 * su contenido real, nunca por la extensión del archivo ni por el tipo que declara el cliente.
 * Referencia: criterios C.1 y C.2 ("formato PNG, JPG o WebP").
 */
public enum ImageFormat {
    PNG,
    JPEG,
    WEBP
}
