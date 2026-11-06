package cr.ac.tec.pokearena.application.port;

import cr.ac.tec.pokearena.domain.error.ImageValidationException;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: puerto del procesador de imágenes (corrección 6 del profesor; participante
 * "Procesador de imágenes" de los diagramas A.1 y A.2).
 * Responsabilidades: validar una imagen según {@link ImageRules} y generar su versión pequeña.
 * La implementación vive en {@code infrastructure.storage}.
 * Referencia: secciones 7.1 y 7.2, pasos 7 y 8.
 */
public interface ImageProcessor {

    /**
     * Paso 7: valida peso, formato real, proporción y resolución mínima, en ese orden.
     *
     * @return el formato detectado y las dimensiones
     * @throws ImageValidationException con el requisito incumplido
     */
    ValidatedImage validate(byte[] content, ImageRules rules);

    /**
     * Paso 8: genera la versión pequeña con el tamaño EXACTO de {@code rules}, en PNG.
     *
     * @return los bytes PNG de la miniatura
     * @throws ThumbnailGenerationException si no se pudo generar (el caso de uso decide qué hacer)
     */
    byte[] generateThumbnail(ValidatedImage image, ImageRules rules);
}
