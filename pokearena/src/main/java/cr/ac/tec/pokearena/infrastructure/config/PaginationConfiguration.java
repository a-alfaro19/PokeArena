package cr.ac.tec.pokearena.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cr.ac.tec.pokearena.application.usecase.Pagination;

/**
 * Propósito: ensamblar la paginación única del sistema (sección 5.5).
 * Responsabilidades: construir {@link Pagination}, que no lleva anotaciones de Spring (sección 4.3),
 * con el tamaño por defecto y el máximo de la configuración. Si los valores no tienen sentido
 * (por ejemplo, el defecto mayor que el máximo), la aplicación no arranca.
 * Referencia: sección 5.5 ({@code app.pagination.default-limit} y {@code app.pagination.max-limit}).
 */
@Configuration
public class PaginationConfiguration {

    @Bean
    public Pagination pagination(@Value("${app.pagination.default-limit}") int defaultLimit,
            @Value("${app.pagination.max-limit}") int maxLimit) {
        return new Pagination(defaultLimit, maxLimit);
    }
}
