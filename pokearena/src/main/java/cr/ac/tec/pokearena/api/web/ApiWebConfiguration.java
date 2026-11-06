package cr.ac.tec.pokearena.api.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Propósito: registrar en Spring MVC los componentes transversales de la API que no son de seguridad.
 * Responsabilidades: agregar {@link IdempotencyKeyHeaderResolver} a los resolvedores de parámetros.
 * Referencia: sección 5.4.
 */
@Configuration
public class ApiWebConfiguration implements WebMvcConfigurer {

    private final IdempotencyKeyHeaderResolver idempotencyKeyResolver;

    public ApiWebConfiguration(IdempotencyKeyHeaderResolver idempotencyKeyResolver) {
        this.idempotencyKeyResolver = idempotencyKeyResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(idempotencyKeyResolver);
    }
}
