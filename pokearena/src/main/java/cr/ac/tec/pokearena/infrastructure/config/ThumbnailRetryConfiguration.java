package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import cr.ac.tec.pokearena.application.port.ImageProcessor;
import cr.ac.tec.pokearena.application.port.ImageStorage;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.ThumbnailRetryUseCase;
import cr.ac.tec.pokearena.domain.repository.PendingImageRepository;

/**
 * Propósito: ensamblar los reintentos de miniaturas y activar las tareas programadas.
 * Responsabilidades: construir {@link ThumbnailRetryUseCase}, que no lleva anotaciones de Spring
 * (sección 4.3), con el máximo de {@code app.images.max-retries}, y activar {@code @Scheduled}
 * ({@code ThumbnailRetryJob}).
 * Referencia: sección 7.1 ("Si falla la miniatura").
 */
@Configuration
@EnableScheduling
public class ThumbnailRetryConfiguration {

    @Bean
    public ThumbnailRetryUseCase thumbnailRetryUseCase(UnitOfWork unitOfWork, PendingImageRepository pending,
            ImageStorage imageStorage, ImageProcessor imageProcessor,
            @Value("${app.images.max-retries}") int maxRetries) {
        return new ThumbnailRetryUseCase(unitOfWork, pending, imageStorage, imageProcessor, maxRetries);
    }
}
