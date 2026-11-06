package cr.ac.tec.pokearena.api.security;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import cr.ac.tec.pokearena.PokearenaApplication;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenVerifier;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;
import cr.ac.tec.pokearena.domain.error.AuthorizationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propósito: autorización de la API con las credenciales propias de PokéArena, sin Spring
 * Security (sección 5.2, RES-01). Es el participante "Seguridad" de los diagramas del Anexo A.
 * Responsabilidades: para cada operación con {@link RequiresRole}, leer
 * {@code Authorization: Bearer}, verificar el token y comprobar el rol; si todo está bien, dejar
 * el {@link AuthenticatedUser} en la solicitud para el controlador.
 * Referencia: sección 5.2 y paso 2 de los diagramas A.1, A.2, A.3 y A.6.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Es un {@code HandlerInterceptor} y no un filtro de servlet: corre dentro de Spring MVC,
 *       así sus excepciones pasan por {@code ApiErrorHandler} y salen en el formato único.</li>
 *   <li>Primero se autentica y después se autoriza: un token inválido es siempre 401, aunque la
 *       operación exija un rol; solo un token válido sin el rol es 403.</li>
 *   <li>Solo se mira el token en las operaciones protegidas. Una operación {@link PublicRoute} lo ignora.</li>
 *   <li>Cerrado por defecto: una operación de la aplicación sin {@link RequiresRole} ni
 *       {@link PublicRoute} se rechaza con 403 sin ejecutar nada, y queda un aviso en el log.
 *       Así olvidar la anotación es un error visible y no una ruta abierta.</li>
 * </ul>
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    /** Atributo de la solicitud donde queda el usuario autenticado. */
    public static final String AUTHENTICATED_USER_ATTRIBUTE = AuthorizationInterceptor.class.getName() + ".user";

    /** Paquete de los controladores propios, a los que se aplica la regla de cerrado por defecto. */
    public static final String APPLICATION_PACKAGE = PokearenaApplication.class.getPackageName();

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationInterceptor.class);

    // Esquema del encabezado Authorization (RFC 6750); se compara sin distinguir mayúsculas.
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenVerifier tokenVerifier;

    public AuthorizationInterceptor(TokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Archivos estáticos, preflight de CORS y otros manejadores que no son controladores: no aplica.
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Access access = accessOf(method);
        if (access.publicRoute()) {
            return true;
        }
        RequiresRole required = access.requiredRole();
        if (required == null) {
            // Cerrado por defecto: la operación no declaró quién puede usarla.
            if (method.getBeanType().getPackageName().startsWith(APPLICATION_PACKAGE)) {
                LOG.warn("Operación rechazada: {}.{} no declara @RequiresRole ni @PublicRoute",
                        method.getBeanType().getSimpleName(), method.getMethod().getName());
                throw AuthorizationException.forbidden("No tiene permiso para esta operación.");
            }
            // Controladores de bibliotecas, fuera del código de PokéArena: no se les aplica la regla.
            return true;
        }

        // 401: sin credenciales, o credenciales inválidas, manipuladas o vencidas.
        AuthenticatedUser user = tokenVerifier.verify(bearerToken(request));

        // 403: credenciales válidas, pero sin ninguno de los roles exigidos.
        if (Arrays.stream(required.value()).noneMatch(role -> role == user.role())) {
            throw AuthorizationException.forbidden("No tiene permiso para esta operación.");
        }

        request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, user);
        return true;
    }

    /**
     * Qué declara una operación: rol exigido, ruta pública o nada.
     *
     * @param requiredRole roles exigidos, o {@code null}
     * @param publicRoute  si es pública
     */
    public record Access(RequiresRole requiredRole, boolean publicRoute) {

        /** La operación declara quién puede usarla (un rol exigido o ruta pública). */
        public boolean isDeclared() {
            return requiredRole != null || publicRoute;
        }
    }

    /**
     * Lee las anotaciones de una operación. Lo declarado en el método manda sobre la clase. Si un
     * mismo nivel tiene las dos, gana {@link RequiresRole}: ante la duda, la opción más segura.
     */
    public static Access accessOf(HandlerMethod method) {
        RequiresRole roleOnMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresRole.class);
        boolean publicOnMethod = AnnotatedElementUtils.hasAnnotation(method.getMethod(), PublicRoute.class);
        if (roleOnMethod != null || publicOnMethod) {
            return new Access(roleOnMethod, publicOnMethod && roleOnMethod == null);
        }
        RequiresRole roleOnClass = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresRole.class);
        boolean publicOnClass = AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PublicRoute.class);
        return new Access(roleOnClass, publicOnClass && roleOnClass == null);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw AuthenticationException.notAuthenticated("Debe iniciar sesión.");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw AuthenticationException.notAuthenticated("Debe iniciar sesión.");
        }
        return token;
    }
}
