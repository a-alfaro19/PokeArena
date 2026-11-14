package cr.ac.tec.pokearena.domain.rule;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.CatalogCriteria;

/**
 * Propósito: validación de los filtros de la historia 3.1.2 "Explorar el catálogo y mi colección"
 * (sección 8.3).
 * Responsabilidades: convertir los parámetros de la solicitud, tal como llegan (texto), en
 * {@link CatalogCriteria}, y rechazar con INVALID_FIELD, indicando el filtro en {@code field} y sus
 * valores permitidos en {@code allowedValues}, cualquier filtro desconocido, repetido, vacío, un tipo
 * inexistente, un costo fuera de 0 a 10, un mínimo mayor que el máximo o un {@code owned} que no sea
 * {@code true} ni {@code false}. La paginación ({@code limit}, {@code cursor}) la valida aparte la
 * aplicación ({@code Pagination}).
 * Referencia: sección 8.3, Anexo A.6 (mensaje 6) y criterio C.6 ("un filtro con un valor inválido es
 * rechazado indicando los valores permitidos").
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Mismo esquema que {@link CardSearchRules} (corrección 7): la lectura común está en
 *       {@link QueryParameters}; aquí quedan el tipo de error y los mensajes de esta historia. El tipo es
 *       INVALID_FIELD y no INVALID_SEARCH_CRITERIA porque así lo pide la sección 8.3 para el catálogo.</li>
 *   <li>Solo existen los filtros de la tabla de 8.3: el cliente no puede enviar un identificador de
 *       jugador; la colección siempre es la de la sesión verificada.</li>
 *   <li>El rango de costo es el mismo de Crear carta ({@link CardFieldRules}): una sola fuente.</li>
 *   <li>Orden de las comprobaciones (siempre el mismo): cada filtro en orden alfabético (desconocido,
 *       repetido o vacío), luego el tipo, el costo mínimo, el máximo, el rango y al final {@code owned}.</li>
 * </ul>
 */
public final class CatalogRules {

    // Nombres de los filtros, iguales a los parámetros de GET /api/v1/catalogo (sección 8.3).
    public static final String TYPE = "type";
    public static final String COST_MIN = "costMin";
    public static final String COST_MAX = "costMax";
    public static final String OWNED = "owned";

    /** Todos los filtros, en el orden de la tabla de 8.3. */
    public static final List<String> FILTERS = List.of(TYPE, COST_MIN, COST_MAX, OWNED);

    private static final List<String> COST_RANGE = List.of(CardFieldRules.MIN_COST + ".." + CardFieldRules.MAX_COST);
    private static final String TRUE = "true";
    private static final List<String> BOOLEANS = List.of(TRUE, "false");

    private CatalogRules() {
    }

    /**
     * Valida los filtros antes de consultar páginas o colecciones (mensaje 6 de A.6). {@code parameters}
     * no debe incluir los de la paginación.
     *
     * @param types tipos existentes: los mismos del catálogo que usa Crear carta
     * @throws ValidationException INVALID_FIELD con el filtro en {@code field} y sus valores permitidos
     */
    public static CatalogCriteria validate(Map<String, List<String>> parameters, List<CardType> types) {
        // Nombres, repeticiones y vacíos, filtro por filtro en orden alfabético.
        for (String name : new TreeSet<>(parameters.keySet())) {
            if (!FILTERS.contains(name)) {
                throw ValidationException.invalidField(name, "El filtro '" + name + "' no existe.", FILTERS);
            }
            if (QueryParameters.valueProblem(parameters.get(name)).isPresent()) {
                throw ValidationException.invalidField(name,
                        "El filtro '" + name + "' debe indicarse una sola vez y no puede estar vacío.", allowed(name, types));
            }
        }

        // Tipo: debe existir en el catálogo de tipos.
        String type = QueryParameters.text(parameters, TYPE);
        if (type != null && !allowed(TYPE, types).contains(type)) {
            throw ValidationException.invalidField(TYPE, "El tipo '" + type + "' no existe.", allowed(TYPE, types));
        }

        // Costo: extremos de 0 a 10 y mínimo no mayor que el máximo.
        Integer min = cost(parameters, COST_MIN);
        Integer max = cost(parameters, COST_MAX);
        if (QueryParameters.inverted(min, max)) {
            throw ValidationException.invalidField(COST_MIN,
                    "El costo mínimo no puede ser mayor que el costo máximo.", COST_RANGE);
        }

        // owned: solo true o false. Boolean.parseBoolean aceptaría en silencio un error de escritura como false.
        String owned = QueryParameters.text(parameters, OWNED);
        if (owned != null && !BOOLEANS.contains(owned)) {
            throw ValidationException.invalidField(OWNED, "El filtro owned debe ser true o false.", BOOLEANS);
        }
        return new CatalogCriteria(type, min, max, TRUE.equals(owned));
    }

    /** Valores permitidos del filtro, para que el cliente pueda corregirlo. */
    private static List<String> allowed(String name, List<CardType> types) {
        return switch (name) {
            case TYPE -> types.stream().map(CardType::code).toList();
            case OWNED -> BOOLEANS;
            default -> COST_RANGE;
        };
    }

    /** Extremo del rango de costo, o {@code null} si no se indicó (rango abierto por ese lado). */
    private static Integer cost(Map<String, List<String>> parameters, String name) {
        // Texto, decimales y desbordamientos tienen el mismo error tipado que un valor fuera de rango.
        return QueryParameters.boundedInteger(parameters, name, CardFieldRules.MIN_COST, CardFieldRules.MAX_COST,
                problem -> ValidationException.invalidField(name, "El costo debe ser un entero entre "
                        + CardFieldRules.MIN_COST + " y " + CardFieldRules.MAX_COST + ".", COST_RANGE));
    }
}
