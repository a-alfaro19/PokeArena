package cr.ac.tec.pokearena.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import cr.ac.tec.pokearena.api.security.PublicRoute;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.CorrelationIdFilter;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.usecase.Pagination;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.CardSearchRules;
import cr.ac.tec.pokearena.domain.rule.CatalogRules;
import cr.ac.tec.pokearena.support.OpenApiContract;
import cr.ac.tec.pokearena.support.OpenApiContract.Operation;
import cr.ac.tec.pokearena.support.OpenApiContract.Parameter;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: prueba de contrato de la API (RNF-API-01; sección 10).
 * Responsabilidades: comparar, EN LOS DOS SENTIDOS, el contrato {@code openapi/pokearena.yaml} con las
 * operaciones que Spring registró de verdad: rutas y métodos, parámetros de consulta, de ruta y
 * encabezados, campos del formulario multipart, tipo de contenido y autorización (pública o con sus
 * roles). Si alguien agrega, quita o cambia una operación sin actualizar el contrato, falla.
 * Referencia: sección 10 y sección 5.2 (cada operación declara quién puede usarla).
 *
 * <p>Cómo se lee la aplicación: de cada método de controlador registrado se toman sus anotaciones
 * ({@code @RequestParam}, {@code @RequestPart}, {@code @PathVariable}, {@code @RequestHeader},
 * {@code @RequiresRole}, {@code @PublicRoute}) y sus tipos de argumento propios
 * ({@link IdempotencyKeyHeader} es el encabezado {@code Idempotency-Key}).
 *
 * <p>Casos especiales, cada uno con su motivo:
 * <ul>
 *   <li>Fuera del contrato: {@code /error} (despacho interno de errores del servidor) y
 *       {@code /api/v1/dev/sesiones} (solo existe con el perfil dev; la especificación pide no
 *       documentarlo).</li>
 *   <li>{@code GET /api/v1/cartas} recibe todos sus parámetros en un {@code MultiValueMap} (para que
 *       el dominio rechace un criterio desconocido con su error tipado), así que sus nombres no están
 *       en la firma: la fuente de verdad son {@link CardSearchRules#CRITERIA} y
 *       {@link Pagination#PARAMETERS}, que es exactamente lo que acepta la aplicación.</li>
 *   <li>{@code X-Correlation-Id} lo atiende un filtro ({@link CorrelationIdFilter}) en todas las rutas,
 *       no un controlador.</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("Contrato OpenAPI: operaciones de la aplicación")
class OpenApiContractIT extends PostgresIntegrationTestBase {

    private static final String ERROR_PATH = "/error";
    private static final String DEV_PATH_PREFIX = "/api/v1/dev/";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String QUERY = "query";
    private static final String HEADER = "header";
    private static final String PATH = "path";

    /** Parámetros de las operaciones que los reciben en un mapa, sin declararlos en la firma. */
    private static final Map<Operation, Set<String>> MAP_QUERY_PARAMETERS = Map.of(
            new Operation("GET", "/api/v1/cartas"), union(CardSearchRules.CRITERIA, Pagination.PARAMETERS),
            new Operation("GET", "/api/v1/catalogo"), union(CatalogRules.FILTERS, Pagination.PARAMETERS));

    /** Encabezados que atiende un filtro en todas las rutas. */
    private static final Set<String> FILTER_HEADERS = Set.of(CorrelationIdFilter.HEADER);

    @Autowired
    private ApplicationContext context;

    private final OpenApiContract contract = OpenApiContract.load();
    private Map<Operation, HandlerMethod> application;

    @BeforeEach
    void readApplication() {
        application = new LinkedHashMap<>();
        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            mapping.getHandlerMethods().forEach((info, handler) -> operationsOf(info).stream()
                    .filter(operation -> !operation.path().equals(ERROR_PATH))
                    .filter(operation -> !operation.path().startsWith(DEV_PATH_PREFIX))
                    .forEach(operation -> application.put(operation, handler)));
        }
    }

    @Test
    @DisplayName("10: cada operación documentada existe en la aplicación, con su ruta y su método")
    void documentedOperationsExist() {
        assertThat(application.keySet()).containsAll(contract.operations());
    }

    @Test
    @DisplayName("10: cada operación de la aplicación está documentada (salvo /error y dev/sesiones)")
    void applicationOperationsAreDocumented() {
        assertThat(contract.operations()).containsAll(application.keySet());
    }

    @Test
    @DisplayName("10: los parámetros de consulta, de ruta y los encabezados coinciden en los dos sentidos")
    void parametersMatch() {
        for (Operation operation : contract.operations()) {
            HandlerMethod handler = application.get(operation);
            if (handler == null) {
                continue; // La informa documentedOperationsExist.
            }
            Set<Parameter> documented = new LinkedHashSet<>(contract.parameters(operation));
            documented.removeIf(parameter -> parameter.in().equals(HEADER) && FILTER_HEADERS.contains(parameter.name()));

            assertThat(documented).as("parámetros de %s", operation)
                    .containsExactlyInAnyOrderElementsOf(parametersOf(operation, handler));
        }
    }

    @Test
    @DisplayName("10: los campos del formulario multipart y el tipo de contenido coinciden en los dos sentidos")
    void formFieldsMatch() {
        for (Operation operation : contract.operations()) {
            HandlerMethod handler = application.get(operation);
            if (handler == null) {
                continue; // La informa documentedOperationsExist.
            }
            assertThat(contract.formFields(operation)).as("campos de %s", operation)
                    .containsExactlyInAnyOrderElementsOf(formFieldsOf(handler));
            assertThat(contract.requestContentTypes(operation)).as("tipo de contenido de %s", operation)
                    .containsExactlyInAnyOrderElementsOf(consumes(operation));
        }
    }

    @Test
    @DisplayName("5.2: la autorización documentada coincide: pública con @PublicRoute, y x-roles igual a @RequiresRole")
    void securityMatches() {
        for (Operation operation : contract.operations()) {
            HandlerMethod handler = application.get(operation);
            if (handler == null) {
                continue; // La informa documentedOperationsExist.
            }
            boolean publicRoute = annotation(handler, PublicRoute.class) != null;
            RequiresRole requiresRole = annotation(handler, RequiresRole.class);
            Set<String> roles = requiresRole == null ? Set.of()
                    : Arrays.stream(requiresRole.value()).map(Role::name).collect(Collectors.toSet());

            assertThat(contract.isPublic(operation)).as("pública: %s", operation).isEqualTo(publicRoute);
            assertThat(contract.roles(operation)).as("roles de %s", operation).containsExactlyInAnyOrderElementsOf(roles);
        }
    }

    @Test
    @DisplayName("5.2 y 5.4: las operaciones protegidas documentan 401 y 403, y los POST de creación documentan 422")
    void errorResponsesAreDocumented() {
        for (Operation operation : contract.operations()) {
            Set<String> codes = contract.responseCodes(operation);
            if (!contract.isPublic(operation)) {
                assertThat(codes).as("respuestas de %s", operation).contains("401", "403");
            }
            if (!application.containsKey(operation) || formFieldsOf(application.get(operation)).isEmpty()) {
                continue;
            }
            assertThat(codes).as("respuestas de %s", operation).contains("201", "400", "415", "422");
        }
    }

    // =========================================================================================
    // Lectura de la aplicación.
    // =========================================================================================

    private static Set<Operation> operationsOf(RequestMappingInfo info) {
        Set<Operation> operations = new LinkedHashSet<>();
        for (String path : info.getPatternValues()) {
            info.getMethodsCondition().getMethods()
                    .forEach(method -> operations.add(new Operation(method.name(), path)));
        }
        return operations;
    }

    private Set<String> consumes(Operation operation) {
        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                if (operationsOf(entry.getKey()).contains(operation)) {
                    return entry.getKey().getConsumesCondition().getConsumableMediaTypes().stream()
                            .map(MediaType::toString).collect(Collectors.toSet());
                }
            }
        }
        return Set.of();
    }

    /** Parámetros de consulta, de ruta y encabezados de un método de controlador. */
    private static Set<Parameter> parametersOf(Operation operation, HandlerMethod handler) {
        Set<Parameter> parameters = new LinkedHashSet<>();
        boolean multipart = !formFieldsOf(handler).isEmpty();
        for (MethodParameter parameter : handler.getMethodParameters()) {
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
            RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
            if (requestParam != null && MultiValueMap.class.isAssignableFrom(parameter.getParameterType())) {
                assertThat(MAP_QUERY_PARAMETERS).as("%s recibe un mapa de parámetros: declare cuáles acepta", operation)
                        .containsKey(operation);
                MAP_QUERY_PARAMETERS.get(operation).forEach(name -> parameters.add(new Parameter(name, QUERY)));
            } else if (requestParam != null && !multipart) {
                parameters.add(new Parameter(nameOf(requestParam.name(), parameter), QUERY));
            } else if (pathVariable != null) {
                parameters.add(new Parameter(nameOf(pathVariable.name(), parameter), PATH));
            } else if (requestHeader != null) {
                parameters.add(new Parameter(nameOf(requestHeader.name(), parameter), HEADER));
            } else if (IdempotencyKeyHeader.class.equals(parameter.getParameterType())) {
                parameters.add(new Parameter(IDEMPOTENCY_HEADER, HEADER));
            }
        }
        return parameters;
    }

    /** Campos del formulario: {@code @RequestPart} y, en un método multipart, {@code @RequestParam}. */
    private static Set<String> formFieldsOf(HandlerMethod handler) {
        Set<String> parts = new LinkedHashSet<>();
        for (MethodParameter parameter : handler.getMethodParameters()) {
            RequestPart part = parameter.getParameterAnnotation(RequestPart.class);
            if (part != null) {
                parts.add(nameOf(part.name(), parameter));
            }
        }
        if (parts.isEmpty()) {
            return parts;
        }
        for (MethodParameter parameter : handler.getMethodParameters()) {
            RequestParam field = parameter.getParameterAnnotation(RequestParam.class);
            if (field != null) {
                parts.add(nameOf(field.name(), parameter));
            }
        }
        return parts;
    }

    private static String nameOf(String declared, MethodParameter parameter) {
        // Sin nombre en la anotación, Spring usa el del parámetro (se compila con -parameters).
        return declared.isEmpty() ? parameter.getParameter().getName() : declared;
    }

    /** La anotación del método o, si no la tiene, la de la clase (lo del método manda, sección 5.2). */
    private static <A extends java.lang.annotation.Annotation> A annotation(HandlerMethod handler, Class<A> type) {
        Method method = handler.getMethod();
        A onMethod = method.getAnnotation(type);
        return onMethod != null ? onMethod : handler.getBeanType().getAnnotation(type);
    }

    private static Set<String> union(java.util.Collection<String> first, java.util.Collection<String> second) {
        Set<String> all = new LinkedHashSet<>(first);
        all.addAll(second);
        return all;
    }
}
