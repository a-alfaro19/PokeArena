package cr.ac.tec.pokearena.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Valida filtros antes de consultar y conserva una representación inequívoca para ligar el cursor.
public record CardSearchCriteria(Map<String, String> values) {
    private static final Set<String> FILTERS = Set.of("code", "name", "cost", "costMin", "costMax",
            "power", "powerMin", "powerMax", "type", "rarity", "abilityCode", "abilityTrigger", "abilityEffect");

    public CardSearchCriteria {
        TreeMap<String, String> ordered = new TreeMap<>(values);
        for (var entry : ordered.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!FILTERS.contains(key)) {
                throw CardSearchException.invalid(key, "Criterio desconocido");
            }
            int maximum = Set.of("code", "abilityCode").contains(key) ? 50 : 100;
            if (value == null || value.isBlank() || value.length() > maximum || value.indexOf(0) >= 0) {
                throw CardSearchException.invalid(key, "Criterio vacío o demasiado largo");
            }
        }
        validateEnum(ordered, "type", CardType.class);
        validateEnum(ordered, "rarity", CardRarity.class);
        validateRange(ordered, "cost", 0, 10);
        validateRange(ordered, "power", -20, 20);
        values = Collections.unmodifiableMap(ordered);
    }

    // Rechaza ambigüedades entre valores exactos y rangos en lugar de ignorar criterios.
    private static void validateRange(Map<String, String> values, String field, int low, int high) {
        Integer exact = number(values, field, low, high);
        Integer minimum = number(values, field + "Min", low, high);
        Integer maximum = number(values, field + "Max", low, high);
        if (exact != null && (minimum != null || maximum != null)) {
            throw CardSearchException.invalid(field, "No combine un valor exacto con un rango");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw CardSearchException.invalid(field, "El mínimo no puede superar el máximo");
        }
    }

    private static Integer number(Map<String, String> values, String field, int low, int high) {
        if (!values.containsKey(field)) { return null; }
        try {
            int value = Integer.parseInt(values.get(field));
            if (value >= low && value <= high) { return value; }
        } catch (NumberFormatException ignored) {
            // Se entrega el mismo error de criterio para enteros inválidos y fuera de límites.
        }
        throw CardSearchException.invalid(field, "Debe ser un entero entre " + low + " y " + high);
    }

    private static <E extends Enum<E>> void validateEnum(Map<String, String> values, String key, Class<E> type) {
        if (!values.containsKey(key)) { return; }
        try {
            Enum.valueOf(type, values.get(key));
        } catch (IllegalArgumentException exception) {
            throw CardSearchException.invalid(key, "Valor inexistente");
        }
    }

    // El prefijo de longitud evita colisiones aunque los textos contengan separadores.
    public String canonical() {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> result.append(key.length()).append(':').append(key)
                .append(value.length()).append(':').append(value));
        return result.toString();
    }
}
