package cr.ac.tec.pokearena.domain.rule;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.CatalogCriteria;

/**
 * Propósito: validar los filtros de Explorar catálogo (8.3 y C.6).
 * Responsabilidades: rechazar filtros desconocidos, repetidos o inválidos con INVALID_FIELD;
 * Pagination valida limit y cursor por separado. No altera las reglas del administrador.
 */
public final class CatalogRules {

    // Solo estos filtros pertenecen al catálogo; no se admite un id de jugador del cliente.
    public static final String TYPE = "type";
    public static final String COST_MIN = "costMin";
    public static final String COST_MAX = "costMax";
    public static final String OWNED = "owned";
    public static final List<String> FILTERS = List.of(TYPE, COST_MIN, COST_MAX, OWNED);
    private static final List<String> COST_RANGE = List.of(CardFieldRules.MIN_COST + ".." + CardFieldRules.MAX_COST);
    private static final List<String> BOOLEANS = List.of("true", "false");

    private CatalogRules() {
    }

    /** Valida antes de consultar páginas o colecciones (mensaje 6 de A.6). */
    public static CatalogCriteria validate(Map<String, List<String>> parameters, List<CardType> types) {
        for (String name : new TreeSet<>(parameters.keySet())) {
            if (!FILTERS.contains(name)) {
                throw ValidationException.invalidField(name, "El filtro '" + name + "' no existe.", FILTERS);
            }
            List<String> values = parameters.get(name);
            if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
                throw ValidationException.invalidField(name,
                        "El filtro '" + name + "' debe indicarse una sola vez y no puede estar vacío.", allowed(name, types));
            }
        }

        // Los tipos válidos vienen del mismo catálogo que usa Crear carta.
        String type = text(parameters, TYPE);
        if (type != null && !allowed(TYPE, types).contains(type)) {
            throw ValidationException.invalidField(TYPE, "El tipo '" + type + "' no existe.", allowed(TYPE, types));
        }
        Integer min = cost(parameters, COST_MIN);
        Integer max = cost(parameters, COST_MAX);
        if (min != null && max != null && min > max) {
            throw ValidationException.invalidField(COST_MIN,
                    "El costo mínimo no puede ser mayor que el costo máximo.", COST_RANGE);
        }

        // Boolean.parseBoolean aceptaría silenciosamente errores de escritura como false.
        String owned = text(parameters, OWNED);
        if (owned != null && !BOOLEANS.contains(owned)) {
            throw ValidationException.invalidField(OWNED, "El filtro owned debe ser true o false.", BOOLEANS);
        }
        return new CatalogCriteria(type, min, max, "true".equals(owned));
    }

    /** Valores permitidos del campo para que el cliente pueda corregirlo. */
    private static List<String> allowed(String name, List<CardType> types) {
        return switch (name) {
            case TYPE -> types.stream().map(CardType::code).toList();
            case OWNED -> BOOLEANS;
            default -> COST_RANGE;
        };
    }

    /** Conserva la ausencia de un extremo del rango como null. */
    private static Integer cost(Map<String, List<String>> parameters, String name) {
        String value = text(parameters, name);
        if (value == null) {
            return null;
        }
        try {
            int cost = Integer.parseInt(value);
            if (cost >= CardFieldRules.MIN_COST && cost <= CardFieldRules.MAX_COST) {
                return cost;
            }
        } catch (NumberFormatException invalid) {
            // Texto, decimales y desbordamientos tienen el mismo error tipado que fuera de rango.
        }
        throw ValidationException.invalidField(name, "El costo debe ser un entero entre "
                + CardFieldRules.MIN_COST + " y " + CardFieldRules.MAX_COST + ".", COST_RANGE);
    }

    /** Normaliza espacios sin convertir los códigos de tipo ni los booleanos. */
    private static String text(Map<String, List<String>> parameters, String name) {
        return parameters.containsKey(name) ? parameters.get(name).getFirst().strip() : null;
    }
}
