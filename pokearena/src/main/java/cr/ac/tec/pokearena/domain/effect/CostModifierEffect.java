package cr.ac.tec.pokearena.domain.effect;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Propósito: plug-in del efecto "Modificador de costo" (criterio C.2): cambia el costo de las cartas
 * que se juegan en la ubicación.
 * Responsabilidades: exigir una cantidad entera de -5 a +5 ({@code amount}) y, si se indica un tipo
 * afectado ({@code affectedType}, opcional), que exista.
 * Referencia: sección 7.2, tabla de plug-ins de efecto.
 */
public final class CostModifierEffect implements LocationEffectType {

    public static final String CODE = "COST_MODIFIER";
    static final String AMOUNT = "amount";
    static final String AFFECTED_TYPE = "affectedType";

    /** Cantidad que modifica el costo: de -5 a +5. */
    public static final int MIN_AMOUNT = -5;
    public static final int MAX_AMOUNT = 5;

    private final Supplier<? extends Collection<String>> existingTypes;

    /** @param existingTypes códigos de tipo de carta que existen (vienen del catálogo, no están fijos aquí) */
    public CostModifierEffect(Supplier<? extends Collection<String>> existingTypes) {
        this.existingTypes = existingTypes;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void validate(Map<String, Object> parameters) {
        EffectParameters.allowOnly(CODE, parameters, Set.of(AMOUNT, AFFECTED_TYPE));
        EffectParameters.requireInteger(CODE, parameters, AMOUNT, MIN_AMOUNT, MAX_AMOUNT);
        EffectParameters.optionalType(CODE, parameters, AFFECTED_TYPE, existingTypes.get());
    }
}
