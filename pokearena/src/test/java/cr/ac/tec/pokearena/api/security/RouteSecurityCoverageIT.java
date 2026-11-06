package cr.ac.tec.pokearena.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import cr.ac.tec.pokearena.api.controller.DevSessionController;
import cr.ac.tec.pokearena.api.error.ApiFallbackErrorController;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: garantizar que la API es cerrada por defecto (sección 5.2).
 * Responsabilidades: recorrer todas las operaciones de controlador registradas en Spring MVC y
 * fallar si alguna de la aplicación no declara {@link RequiresRole} ni {@link PublicRoute}. Así un
 * controlador nuevo sin anotación se detecta en {@code verify}, antes de llegar a producción.
 * Referencia: sección 5.2 ("las rutas sin anotación se rechazan").
 *
 * <p>Se usa el perfil dev para incluir también las rutas que solo existen en desarrollo
 * ({@code POST /api/v1/dev/sesiones}). Como en {@code DevSessionControllerIT}, se mantiene el
 * logging predeterminado para no cambiar el formato de los logs de las demás pruebas.
 */
@SpringBootTest(properties = { "logging.structured.format.console=ecs", "logging.pattern.level=%5p" })
@ActiveProfiles("dev")
@DisplayName("Cobertura de seguridad de las rutas")
class RouteSecurityCoverageIT extends PostgresIntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Toda operación de controlador declara @RequiresRole o @PublicRoute (salvo /error)")
    void everyRouteDeclaresItsAccess() {
        List<String> undeclared = new ArrayList<>();
        int checked = 0;

        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> route : mapping.getHandlerMethods().entrySet()) {
                HandlerMethod method = route.getValue();
                if (!isApplicationRoute(method)) {
                    continue;
                }
                checked++;
                if (!AuthorizationInterceptor.accessOf(method).isDeclared()) {
                    undeclared.add(route.getKey() + " -> " + method.getBeanType().getSimpleName() + "."
                            + method.getMethod().getName());
                }
            }
        }

        assertThat(undeclared)
                .as("Operaciones sin @RequiresRole ni @PublicRoute (quedarían rechazadas con 403)")
                .isEmpty();
        // Si el recorrido no encontrara rutas, la prueba pasaría sin verificar nada.
        assertThat(checked).as("Operaciones revisadas").isPositive();
    }

    @Test
    @DisplayName("El recorrido incluye las credenciales de prueba, que son @PublicRoute")
    void devSessionsAreCoveredAndPublic() {
        HandlerMethod devSessions = context.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
                .flatMap(mapping -> mapping.getHandlerMethods().values().stream())
                .filter(method -> method.getBeanType().equals(DevSessionController.class))
                .findFirst()
                .orElseThrow();

        assertThat(AuthorizationInterceptor.accessOf(devSessions).publicRoute()).isTrue();
    }

    /** Operaciones propias de PokéArena, sin el controlador de /error (excluido a propósito). */
    private static boolean isApplicationRoute(HandlerMethod method) {
        return method.getBeanType().getPackageName().startsWith(AuthorizationInterceptor.APPLICATION_PACKAGE)
                && !ApiFallbackErrorController.class.equals(method.getBeanType());
    }
}
