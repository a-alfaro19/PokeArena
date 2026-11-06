package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.IdempotencyGuard;
import cr.ac.tec.pokearena.application.usecase.ImageCreationSteps;

/**
 * Propósito: ensamblar los pasos comunes de las historias que crean una entidad con imagen.
 * Responsabilidades: construir {@link ImageCreationSteps}, que comparten Crear carta y Crear ubicación.
 * Referencia: secciones 7.1 y 7.2 (pasos 8 y 10).
 */
@Configuration
public class ImageCreationConfiguration {

    @Bean
    public ImageCreationSteps imageCreationSteps(IdempotencyGuard idempotency, ImageProcessor imageProcessor,
            ImageStorage imageStorage, UnitOfWork unitOfWork) {
        return new ImageCreationSteps(idempotency, imageProcessor, imageStorage, unitOfWork);
    }
}
