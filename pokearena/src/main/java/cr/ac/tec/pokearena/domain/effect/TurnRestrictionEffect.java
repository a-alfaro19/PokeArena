package cr.ac.tec.pokearena.domain.effect;

import java.util.Map;
import java.util.Set;

/**
 * Propósito: plug-in del efecto "Restricción por turno" (criterio C.2): turnos en los que no se pueden
 * jugar cartas en la ubicación.
 * Responsabilidades: exigir {@code turns}, una lista no vacía de turnos enteros de 1 a 6, sin repetidos.
 * Referencia: sección 7.2, tabla de plug-ins de efecto.
 */
public final class TurnRestrictionEffect implements LocationEffectType {

    public static final String CODE = "TURN_RESTRICTION";
    static final String TURNS = "turns";

    /** Turnos de una partida: del 1 al 6. */
    public static final int FIRST_TURN = 1;
    public static final int LAST_TURN = 6;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void validate(Map<String, Object> parameters) {
        EffectParameters.allowOnly(CODE, parameters, Set.of(TURNS));
        EffectParameters.requireDistinctIntegers(CODE, parameters, TURNS, FIRST_TURN, LAST_TURN);
    }
}
