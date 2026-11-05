package cr.ac.tec.pokearena.api.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propósito: dar a cada solicitud un identificador de correlación (sección 5.6).
 * Responsabilidades: tomar {@code X-Correlation-Id} si es válido o generar uno; ponerlo en el
 * MDC (así aparece en cada línea de log JSON), en un atributo de la solicitud (para el cuerpo de
 * los errores) y en el encabezado de la respuesta.
 * Referencia: sección 5.6.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Es el primer filtro ({@code HIGHEST_PRECEDENCE}): todo lo que se registre después, incluida
 *       la seguridad, ya lleva el identificador.</li>
 *   <li>Un valor recibido solo se acepta si tiene caracteres seguros y una longitud acotada. Si no,
 *       se genera uno nuevo: así un cliente no puede inyectar texto en los logs.</li>
 *   <li>El identificador también se guarda como atributo de la solicitud porque el MDC se limpia al
 *       terminar el filtro, y el despacho de error de Tomcat ({@code /error}) ocurre después.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Encabezado de entrada y de salida. */
    public static final String HEADER = "X-Correlation-Id";

    /** Clave en el MDC; es el nombre del campo en los logs JSON. */
    public static final String MDC_KEY = "correlationId";

    /** Atributo de la solicitud donde queda el identificador. */
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";

    // Largo máximo aceptado de un identificador recibido; un UUID mide 36.
    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_LENGTH + "}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = acceptOrGenerate(request.getHeader(HEADER));

        // Se publica antes de seguir para que esté presente aunque la solicitud termine en error.
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // El hilo vuelve al pool de Tomcat: no debe arrastrar el identificador a otra solicitud.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Devuelve el identificador de la solicitud, o {@code null} si el filtro no se ejecutó.
     */
    public static String currentId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof String id ? id : MDC.get(MDC_KEY);
    }

    static String acceptOrGenerate(String received) {
        if (received != null && SAFE_VALUE.matcher(received).matches()) {
            return received;
        }
        return UUID.randomUUID().toString();
    }
}
