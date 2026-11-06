package cr.ac.tec.pokearena.domain.effect;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Propósito: plug-in del efecto "Bonificación por tipo" (criterio C.2).
 * Responsabilidades: exigir un tipo de carta existente ({@code type}) y una cantidad de poder entera de
 * -5 a +5 ({@code amount}).
 * Referencia: sección 7.2, tabla de plug-ins de efecto.
 */
public final class TypeBonusEffect implements LocationEffectType {

    public static final String CODE = "TYPE_BONUS";
    static final String TYPE = "type";
    static final String AMOUNT = "amount";

    /** Cantidad de poder: de -5 a +5. */
    public static final int MIN_AMOUNT = -5;
    public static final int MAX_AMOUNT = 5;

    private final Supplier<? extends Collection<String>> existingTypes;

    /** @param existingTypes códigos de tipo de carta que existen (vienen del catálogo, no están fijos aquí) */
    public TypeBonusEffect(Supplier<? extends Collection<String>> existingTypes) {
        this.existingTypes = existingTypes;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void validate(Map<String, Object> parameters) {
        EffectParameters.allowOnly(CODE, parameters, Set.of(TYPE, AMOUNT));
        EffectParameters.requireType(CODE, parameters, TYPE, existingTypes.get());
        EffectParameters.requireInteger(CODE, parameters, AMOUNT, MIN_AMOUNT, MAX_AMOUNT);
    }
}
