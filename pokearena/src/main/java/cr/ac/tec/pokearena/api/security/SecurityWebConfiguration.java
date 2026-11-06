package cr.ac.tec.pokearena.api.security;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Propósito: conectar la autorización propia con Spring MVC (sección 5.2).
 * Responsabilidades: registrar {@link AuthorizationInterceptor} para todas las rutas y
 * {@link AuthenticatedUserArgumentResolver} para los parámetros de los controladores.
 * Referencia: sección 5.2.
 */
@Configuration
public class SecurityWebConfiguration implements WebMvcConfigurer {

    private final AuthorizationInterceptor authorizationInterceptor;
    private final AuthenticatedUserArgumentResolver authenticatedUserResolver;

    public SecurityWebConfiguration(AuthorizationInterceptor authorizationInterceptor,
            AuthenticatedUserArgumentResolver authenticatedUserResolver) {
        this.authorizationInterceptor = authorizationInterceptor;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Para todas las rutas: el interceptor decide según @RequiresRole, no según la URL.
        registry.addInterceptor(authorizationInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedUserResolver);
    }
}
