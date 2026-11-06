package cr.ac.tec.pokearena.domain.rule;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;

/**
 * Propósito: validación de los criterios de la búsqueda de cartas (historia 2.1.2; sección 7.3).
 * Responsabilidades: convertir los parámetros de la solicitud, tal como llegan (texto), en
 * {@link CardSearchCriteria}, y rechazar con INVALID_SEARCH_CRITERIA, indicando el criterio en
 * {@code field}, cualquier criterio desconocido, repetido, vacío, fuera de rango, un tipo o rareza
 * inexistente, un valor exacto combinado con un rango del mismo campo o un mínimo mayor que el máximo.
 * Referencia: sección 7.3 y criterio C.3 ("un criterio con un valor inválido es rechazado indicando
 * el criterio").
 *
 * <p>Portado de {@code CardSearchCriteria} del código original del Sprint 1 (etiqueta
 * {@code sprint1-original}) con los nombres de la especificación
 * ({@code ability} en lugar de {@code abilityCode}) y los errores tipados del formato único. Los
 * rangos de costo y poder son los mismos de Crear carta ({@link CardFieldRules}): una sola fuente.
 *
 * <p>Orden de las comprobaciones (siempre el mismo, para que el error sea predecible): primero los
 * nombres (desconocidos, en orden alfabético), luego las repeticiones y los valores vacíos, y al
 * final cada criterio en el orden de la tabla de 7.3.
 */
public final class CardSearchRules {

    // Nombres de los criterios, iguales a los parámetros de GET /api/v1/cartas (sección 7.3).
    public static final String CODE = "code";
    public static final String NAME = "name";
    public static final String COST = "cost";
    public static final String COST_MIN = "costMin";
    public static final String COST_MAX = "costMax";
    public static final String POWER = "power";
    public static final String POWER_MIN = "powerMin";
    public static final String POWER_MAX = "powerMax";
    public static final String TYPE = "type";
    public static final String RARITY = "rarity";
    public static final String ABILITY = "ability";
    public static final String ABILITY_TRIGGER = "abilityTrigger";
    public static final String ABILITY_EFFECT = "abilityEffect";

    /** Todos los criterios, en el orden de la tabla de 7.3. */
    public static final List<String> CRITERIA = List.of(CODE, NAME, COST, COST_MIN, COST_MAX, POWER, POWER_MIN,
            POWER_MAX, TYPE, RARITY, ABILITY, ABILITY_TRIGGER, ABILITY_EFFECT);

    private CardSearchRules() {
    }

    /**
     * Valida los criterios. {@code parameters} no debe incluir los de la paginación ({@code limit},
     * {@code cursor}): los valida aparte la aplicación.
     *
     * @throws ValidationException INVALID_SEARCH_CRITERIA con el criterio en {@code field}
     */
    public static CardSearchCriteria validate(Map<String, List<String>> parameters, List<CardType> types,
            List<Rarity> rarities) {
        rejectUnknown(parameters);
        parameters.forEach(CardSearchRules::rejectRepeatedOrBlank);

        String code = text(parameters, CODE);
        String nameFragment = nameFragment(parameters);
        Range cost = range(parameters, COST, COST_MIN, COST_MAX, "el costo",
                CardFieldRules.MIN_COST, CardFieldRules.MAX_COST);
        Range power = range(parameters, POWER, POWER_MIN, POWER_MAX, "el poder",
                CardFieldRules.MIN_POWER, CardFieldRules.MAX_POWER);

        CardSearchCriteria.Builder criteria = CardSearchCriteria.builder()
                .code(code)
                .nameFragment(nameFragment)
                .costBetween(cost.min(), cost.max())
                .powerBetween(power.min(), power.max())
                .type(catalogValue(parameters, TYPE, "El tipo", types.stream().map(CardType::code).toList()))
                .rarity(catalogValue(parameters, RARITY, "La rareza", rarities.stream().map(Rarity::code).toList()))
                .ability(text(parameters, ABILITY))
                .abilityTrigger(text(parameters, ABILITY_TRIGGER))
                .abilityEffect(text(parameters, ABILITY_EFFECT));
        return criteria.build();
    }

    // ---------------------------------------------------------------------------------------------
    // Nombres, repeticiones y vacíos.
    // ---------------------------------------------------------------------------------------------

    private static void rejectUnknown(Map<String, List<String>> parameters) {
        // TreeSet: si hay varios desconocidos, se informa siempre el primero en orden alfabético.
        for (String name : new TreeSet<>(parameters.keySet())) {
            if (!CRITERIA.contains(name)) {
                throw ValidationException.invalidSearchCriteria(name,
                        "El criterio de búsqueda '" + name + "' no existe.", CRITERIA);
            }
        }
    }

    private static void rejectRepeatedOrBlank(String name, List<String> values) {
        if (values.size() > 1) {
            throw ValidationException.invalidSearchCriteria(name,
                    "El criterio '" + name + "' solo puede indicarse una vez.", null);
        }
        if (values.isEmpty() || values.getFirst() == null || values.getFirst().isBlank()) {
            throw ValidationException.invalidSearchCriteria(name,
                    "El criterio '" + name + "' no puede estar vacío.", null);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Valores.
    // ---------------------------------------------------------------------------------------------

    /** Valor del criterio sin espacios al inicio ni al final, o {@code null} si no se indicó. */
    private static String text(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : values.getFirst().strip();
    }

    /**
     * Parte del nombre en la misma forma que {@code card.name_normalized} (minúsculas y sin tildes),
     * para que "DRAGON" encuentre "Dragón". El escape de {@code %} y {@code _} es de la consulta SQL
     * y lo hace la infraestructura: aquí el texto se conserva tal cual.
     */
    private static String nameFragment(Map<String, List<String>> parameters) {
        String name = text(parameters, NAME);
        return name == null ? null : NameNormalizer.normalize(name);
    }

    /** Rango de un campo numérico; un extremo {@code null} es un rango abierto por ese lado. */
    private record Range(Integer min, Integer max) {
    }

    /**
     * Rango de costo o poder. Un valor exacto ({@code cost=3}) es el rango de un solo valor
     * ({@code 3..3}). Rechaza combinar el valor exacto con un rango del mismo campo y un mínimo mayor
     * que el máximo; en ambos casos el criterio informado es el del campo ({@code cost} o {@code power}).
     */
    private static Range range(Map<String, List<String>> parameters, String exactName, String minName,
            String maxName, String label, int lowest, int highest) {
        Integer exact = integer(parameters, exactName, lowest, highest);
        Integer min = integer(parameters, minName, lowest, highest);
        Integer max = integer(parameters, maxName, lowest, highest);
        if (exact != null && (min != null || max != null)) {
            throw ValidationException.invalidSearchCriteria(exactName, "No combine un valor exacto de " + label
                    + " ('" + exactName + "') con un rango ('" + minName + "' o '" + maxName + "').", null);
        }
        if (min != null && max != null && min > max) {
            throw ValidationException.invalidSearchCriteria(exactName, "El mínimo de " + label + " (" + min
                    + ") no puede ser mayor que el máximo (" + max + ").", null);
        }
        return exact != null ? new Range(exact, exact) : new Range(min, max);
    }

    /** Entero entre {@code lowest} y {@code highest}, o {@code null} si el criterio no se indicó. */
    private static Integer integer(Map<String, List<String>> parameters, String name, int lowest, int highest) {
        String value = text(parameters, name);
        if (value == null) {
            return null;
        }
        List<String> allowed = List.of(lowest + ".." + highest);
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException notAnInteger) {
            // Un decimal ("1.5") o un texto ("abc") tienen el mismo error que un valor fuera de rango.
            throw ValidationException.invalidSearchCriteria(name, "El criterio '" + name
                    + "' debe ser un número entero entre " + lowest + " y " + highest + ".", allowed);
        }
        if (number < lowest || number > highest) {
            throw ValidationException.invalidSearchCriteria(name, "El criterio '" + name
                    + "' debe estar entre " + lowest + " y " + highest + ".", allowed);
        }
        return number;
    }

    /** Código de un catálogo (tipo o rareza) que debe existir, o {@code null} si no se indicó. */
    private static String catalogValue(Map<String, List<String>> parameters, String name, String label,
            List<String> allowed) {
        String value = text(parameters, name);
        if (value != null && !allowed.contains(value)) {
            throw ValidationException.invalidSearchCriteria(name, label + " '" + value + "' no existe.", allowed);
        }
        return value;
    }
}
