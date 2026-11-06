package cr.ac.tec.pokearena.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Propósito: criterios ya validados de la búsqueda de cartas del administrador (historia 2.1.2).
 * Responsabilidades: llevar los criterios activos, todos opcionales, hasta la consulta. Los que faltan
 * no filtran; los presentes se combinan con AND (criterio C.3). Un valor exacto de costo o poder se
 * guarda como un rango de un solo valor (mínimo = máximo), así la consulta tiene una sola forma.
 * Referencia: sección 7.3 y patrón Builder de la sección 4.4 ("parámetros opcionales sin
 * constructores enormes").
 *
 * <p>Se arma con {@link #builder()}; solo lo hace {@code CardSearchRules}, después de validar cada
 * valor. Sin criterios ({@code builder().build()}) recorre todo el catálogo.
 */
public final class CardSearchCriteria {

    private final String code;
    private final String nameFragment;
    private final Integer costMin;
    private final Integer costMax;
    private final Integer powerMin;
    private final Integer powerMax;
    private final String typeCode;
    private final String rarityCode;
    private final String abilityCode;
    private final String abilityTrigger;
    private final String abilityEffect;

    private CardSearchCriteria(Builder builder) {
        this.code = builder.code;
        this.nameFragment = builder.nameFragment;
        this.costMin = builder.costMin;
        this.costMax = builder.costMax;
        this.powerMin = builder.powerMin;
        this.powerMax = builder.powerMax;
        this.typeCode = builder.typeCode;
        this.rarityCode = builder.rarityCode;
        this.abilityCode = builder.abilityCode;
        this.abilityTrigger = builder.abilityTrigger;
        this.abilityEffect = builder.abilityEffect;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Código exacto de la carta. */
    public Optional<String> code() {
        return Optional.ofNullable(code);
    }

    /** Parte del nombre, ya normalizada (minúsculas y sin tildes), para la coincidencia parcial. */
    public Optional<String> nameFragment() {
        return Optional.ofNullable(nameFragment);
    }

    /** Costo mínimo, incluido. */
    public Optional<Integer> costMin() {
        return Optional.ofNullable(costMin);
    }

    /** Costo máximo, incluido. */
    public Optional<Integer> costMax() {
        return Optional.ofNullable(costMax);
    }

    /** Poder mínimo, incluido. */
    public Optional<Integer> powerMin() {
        return Optional.ofNullable(powerMin);
    }

    /** Poder máximo, incluido. */
    public Optional<Integer> powerMax() {
        return Optional.ofNullable(powerMax);
    }

    public Optional<String> typeCode() {
        return Optional.ofNullable(typeCode);
    }

    public Optional<String> rarityCode() {
        return Optional.ofNullable(rarityCode);
    }

    /** Código exacto de la habilidad (incluye {@code HAB-000}). */
    public Optional<String> abilityCode() {
        return Optional.ofNullable(abilityCode);
    }

    public Optional<String> abilityTrigger() {
        return Optional.ofNullable(abilityTrigger);
    }

    public Optional<String> abilityEffect() {
        return Optional.ofNullable(abilityEffect);
    }

    /**
     * Criterios activos como pares nombre-valor ordenados por nombre. Dos búsquedas equivalentes dan el
     * mismo resultado (por ejemplo {@code cost=3} y {@code costMin=3&costMax=3}), por eso sirve para
     * la clave de la caché (sección 9).
     */
    public Map<String, String> asMap() {
        Map<String, String> values = new TreeMap<>();
        put(values, "code", code);
        put(values, "name", nameFragment);
        put(values, "costMin", costMin);
        put(values, "costMax", costMax);
        put(values, "powerMin", powerMin);
        put(values, "powerMax", powerMax);
        put(values, "type", typeCode);
        put(values, "rarity", rarityCode);
        put(values, "ability", abilityCode);
        put(values, "abilityTrigger", abilityTrigger);
        put(values, "abilityEffect", abilityEffect);
        return Collections.unmodifiableMap(values);
    }

    private static void put(Map<String, String> values, String name, Object value) {
        if (value != null) {
            values.put(name, String.valueOf(value));
        }
    }

    /** Arma los criterios uno por uno; cada método es opcional. */
    public static final class Builder {

        private String code;
        private String nameFragment;
        private Integer costMin;
        private Integer costMax;
        private Integer powerMin;
        private Integer powerMax;
        private String typeCode;
        private String rarityCode;
        private String abilityCode;
        private String abilityTrigger;
        private String abilityEffect;

        private Builder() {
        }

        public Builder code(String value) {
            this.code = value;
            return this;
        }

        /** Parte del nombre ya normalizada. */
        public Builder nameFragment(String value) {
            this.nameFragment = value;
            return this;
        }

        /** Rango de costo; cualquiera de los dos extremos puede ser {@code null} (rango abierto). */
        public Builder costBetween(Integer min, Integer max) {
            this.costMin = min;
            this.costMax = max;
            return this;
        }

        /** Rango de poder; cualquiera de los dos extremos puede ser {@code null} (rango abierto). */
        public Builder powerBetween(Integer min, Integer max) {
            this.powerMin = min;
            this.powerMax = max;
            return this;
        }

        public Builder type(String value) {
            this.typeCode = value;
            return this;
        }

        public Builder rarity(String value) {
            this.rarityCode = value;
            return this;
        }

        public Builder ability(String value) {
            this.abilityCode = value;
            return this;
        }

        public Builder abilityTrigger(String value) {
            this.abilityTrigger = value;
            return this;
        }

        public Builder abilityEffect(String value) {
            this.abilityEffect = value;
            return this;
        }

        public CardSearchCriteria build() {
            return new CardSearchCriteria(this);
        }
    }
}
