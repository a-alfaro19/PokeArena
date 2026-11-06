package cr.ac.tec.pokearena.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * Propósito: lector del contrato {@code openapi/pokearena.yaml} para las pruebas de contrato.
 * Responsabilidades: cargar el YAML y responder preguntas simples sobre él (operaciones, parámetros por
 * ubicación, campos del formulario, seguridad, respuestas y tipos de error), resolviendo las
 * referencias {@code $ref} internas.
 * Referencia: sección 10 ("una prueba de contrato verifica que las rutas, métodos y parámetros del
 * contrato existen en la aplicación").
 *
 * <p>Usa SnakeYAML, que ya llega con Spring Boot: no agrega dependencias ni generadores de código.
 */
public final class OpenApiContract {

    /** Ubicación del contrato en el classpath. */
    public static final String RESOURCE = "/openapi/pokearena.yaml";

    private static final String REF = "$ref";
    private static final String LOCAL_REF_PREFIX = "#/";
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

    /** Una operación: método HTTP en mayúsculas y ruta con variables entre llaves. */
    public record Operation(String method, String path) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** Un parámetro: nombre y ubicación ({@code query}, {@code header}, {@code path}). */
    public record Parameter(String name, String in) {
    }

    private final Map<String, Object> root;

    private OpenApiContract(Map<String, Object> root) {
        this.root = root;
    }

    /** Carga el contrato del classpath. */
    public static OpenApiContract load() {
        try (InputStream input = OpenApiContract.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("No existe el contrato " + RESOURCE);
            }
            return new OpenApiContract(new Yaml().load(input));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Todas las operaciones documentadas. */
    public Set<Operation> operations() {
        Set<Operation> operations = new LinkedHashSet<>();
        map(root.get("paths")).forEach((path, item) -> map(item).keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> operations.add(new Operation(method.toUpperCase(Locale.ROOT), path))));
        return operations;
    }

    /** Parámetros de la operación (resueltos), con su ubicación. */
    public Set<Parameter> parameters(Operation operation) {
        Set<Parameter> parameters = new LinkedHashSet<>();
        for (Object raw : list(operationNode(operation).get("parameters"))) {
            Map<String, Object> parameter = map(resolve(raw));
            parameters.add(new Parameter((String) parameter.get("name"), (String) parameter.get("in")));
        }
        return parameters;
    }

    /** Tipos de contenido del cuerpo de la solicitud; vacío si la operación no tiene cuerpo. */
    public Set<String> requestContentTypes(Operation operation) {
        Object body = operationNode(operation).get("requestBody");
        return body == null ? Set.of() : map(map(resolve(body)).get("content")).keySet();
    }

    /** Campos del formulario {@code multipart/form-data}; vacío si no tiene. */
    public Set<String> formFields(Operation operation) {
        Object body = operationNode(operation).get("requestBody");
        if (body == null) {
            return Set.of();
        }
        Object multipart = map(map(resolve(body)).get("content")).get("multipart/form-data");
        if (multipart == null) {
            return Set.of();
        }
        Map<String, Object> schema = map(resolve(map(multipart).get("schema")));
        return map(schema.get("properties")).keySet();
    }

    /** Si la operación es pública ({@code security: []}). */
    public boolean isPublic(Operation operation) {
        Object security = operationNode(operation).containsKey("security")
                ? operationNode(operation).get("security")
                : root.get("security");
        return list(security).isEmpty();
    }

    /** Roles de {@code x-roles}; vacío si no los declara. */
    public Set<String> roles(Operation operation) {
        return new LinkedHashSet<>(strings(operationNode(operation).get("x-roles")));
    }

    /** Códigos de respuesta documentados, como texto ("201", "400", ...). */
    public Set<String> responseCodes(Operation operation) {
        return map(operationNode(operation).get("responses")).keySet();
    }

    /** Valores del enum de {@code type} en el esquema {@code ApiError}. */
    public Set<String> errorTypes() {
        Map<String, Object> type = map(map(schema("ApiError").get("properties")).get("type"));
        return new LinkedHashSet<>(strings(type.get("enum")));
    }

    /** Todos los valores de {@code x-error-types} usados en el contrato (operaciones y respuestas reutilizables). */
    public Set<String> referencedErrorTypes() {
        Set<String> types = new LinkedHashSet<>();
        collectErrorTypes(root, types);
        return types;
    }

    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> operationNode(Operation operation) {
        Map<String, Object> item = map(map(root.get("paths")).get(operation.path()));
        Object node = item.get(operation.method().toLowerCase(Locale.ROOT));
        if (node == null) {
            throw new IllegalArgumentException("El contrato no documenta " + operation);
        }
        return map(node);
    }

    private Map<String, Object> schema(String name) {
        return map(map(map(root.get("components")).get("schemas")).get(name));
    }

    /** Sigue un {@code $ref} interno ("#/components/..."), si lo hay. */
    private Object resolve(Object node) {
        if (node instanceof Map<?, ?> object && object.get(REF) instanceof String reference) {
            if (!reference.startsWith(LOCAL_REF_PREFIX)) {
                throw new IllegalStateException("Solo se admiten referencias internas: " + reference);
            }
            Object target = root;
            for (String part : reference.substring(LOCAL_REF_PREFIX.length()).split("/")) {
                target = map(target).get(part);
                if (target == null) {
                    throw new IllegalStateException("Referencia rota en el contrato: " + reference);
                }
            }
            return resolve(target);
        }
        return node;
    }

    private static void collectErrorTypes(Object node, Set<String> types) {
        if (node instanceof Map<?, ?> object) {
            object.forEach((key, value) -> {
                if ("x-error-types".equals(key)) {
                    types.addAll(strings(value));
                } else {
                    collectErrorTypes(value, types);
                }
            });
        } else if (node instanceof Collection<?> items) {
            items.forEach(item -> collectErrorTypes(item, types));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return node == null ? Map.of() : new LinkedHashMap<>((Map<String, Object>) node);
    }

    private static List<?> list(Object node) {
        return node == null ? List.of() : (List<?>) node;
    }

    private static List<String> strings(Object node) {
        List<String> values = new ArrayList<>();
        list(node).forEach(value -> values.add(String.valueOf(value)));
        return values;
    }
}
