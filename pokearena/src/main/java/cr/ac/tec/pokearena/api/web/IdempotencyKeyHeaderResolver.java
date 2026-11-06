package cr.ac.tec.pokearena.api.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import cr.ac.tec.pokearena.domain.error.IdempotencyException;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Propósito: exigir y validar el encabezado {@code Idempotency-Key} en los POST de creación (sección 5.4).
 * Responsabilidades: entregar un {@link IdempotencyKeyHeader} a todo controlador que lo declare como
 * parámetro; rechazar la clave ausente o vacía (IDEMPOTENCY_KEY_REQUIRED) y la demasiado larga
 * (INVALID_FIELD); y nombrar la operación con el método y la ruta del controlador.
 * Referencia: sección 5.4 y paso 1 de los diagramas A.1 y A.2.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Es un resolvedor de parámetros y no un filtro: así lo reutilizan los controladores de
 *       cartas y ubicaciones con solo declarar el parámetro, y sus errores pasan por el manejador
 *       global con el formato único.</li>
 *   <li>Corre después de la autorización: una solicitud sin credenciales recibe 401 aunque además le
 *       falte la clave, sin revelar nada más de la operación.</li>
 *   <li>La operación es "método + ruta del controlador" (por ejemplo {@code POST /api/v1/cartas}),
 *       tomada de Spring MVC y no escrita a mano en cada controlador.</li>
 * </ul>
 */
@Component
public class IdempotencyKeyHeaderResolver implements HandlerMethodArgumentResolver {

    private final int maxKeyLength;

    public IdempotencyKeyHeaderResolver(@Value("${app.idempotency.max-key-length}") int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return IdempotencyKeyHeader.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String value = webRequest.getHeader(IdempotencyException.IDEMPOTENCY_KEY_FIELD);

        // Paso 1 del diagrama: sin clave no se puede garantizar que un reintento no duplique.
        if (value == null || value.isBlank()) {
            throw IdempotencyException.required("Falta el encabezado Idempotency-Key.");
        }
        if (value.length() > maxKeyLength) {
            throw ValidationException.invalidField(IdempotencyException.IDEMPOTENCY_KEY_FIELD,
                    "La clave Idempotency-Key no puede tener más de " + maxKeyLength + " caracteres.",
                    List.of("1.." + maxKeyLength));
        }
        return new IdempotencyKeyHeader(value, endpointOf(webRequest));
    }

    private static String endpointOf(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object pattern = webRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        String path = pattern != null ? pattern.toString() : request.getRequestURI();
        return request.getMethod() + " " + path;
    }
}
