package cr.ac.tec.pokearena.api.security;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.domain.error.AuthenticationException;

/**
 * Propósito: entregar el {@link AuthenticatedUser} a los controladores como un parámetro más.
 * Responsabilidades: tomar el usuario que dejó {@link AuthorizationInterceptor} en la solicitud,
 * para que un controlador escriba {@code create(AuthenticatedUser user, ...)} sin leer encabezados.
 * Referencia: sección 5.2.
 *
 * <p>Si un método pide el usuario pero no tiene {@link RequiresRole}, no hay usuario verificado:
 * se responde 401 en lugar de entregar un valor nulo.
 */
@Component
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Object user = request.getAttribute(AuthorizationInterceptor.AUTHENTICATED_USER_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        if (user instanceof AuthenticatedUser authenticated) {
            return authenticated;
        }
        throw AuthenticationException.notAuthenticated("Debe iniciar sesión.");
    }
}
