package cr.ac.tec.pokearena.support;

import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ValidatedImage;
import cr.ac.tec.pokearena.domain.error.ThumbnailGenerationException;
import cr.ac.tec.pokearena.domain.rule.ImageRules;

/**
 * Propósito: procesador de imágenes para las pruebas de integración que puede simular que la miniatura
 * falla (sección 7.1, "Si falla la miniatura"; ADR-002).
 * Responsabilidades: delegar en el procesador real y, cuando se pide, lanzar
 * {@link ThumbnailGenerationException} al generar la miniatura. Lo comparten las pruebas de Crear carta
 * y Crear ubicación.
 * Referencia: sección 13 ("fallo simulado de la miniatura").
 */
public class FaultyImageProcessor implements ImageProcessor {

    private final ImageProcessor real;

    /** Si es {@code true}, la próxima miniatura falla (y las siguientes, hasta volver a {@code false}). */
    public volatile boolean failThumbnail;

    public FaultyImageProcessor(ImageProcessor real) {
        this.real = real;
    }

    @Override
    public ValidatedImage validate(byte[] content, ImageRules rules) {
        return real.validate(content, rules);
    }

    @Override
    public byte[] generateThumbnail(ValidatedImage image, ImageRules rules) {
        if (failThumbnail) {
            throw new ThumbnailGenerationException("Falla simulada de la miniatura", null);
        }
        return real.generateThumbnail(image, rules);
    }
}
