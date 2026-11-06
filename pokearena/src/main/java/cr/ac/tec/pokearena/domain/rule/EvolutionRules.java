package cr.ac.tec.pokearena.domain.rule;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.CardCode;

/**
 * Propósito: reglas de evolución de las cartas (sección 7.1, "EvolutionRules"; criterios de C.1).
 * Responsabilidades: rechazar con INVALID_EVOLUTION que una carta evolucione a sí misma y que su
 * evolución forme un ciclo (A evoluciona a B y B, directa o indirectamente, a A).
 * Referencia: sección 7.1 y criterios de C.1 (error y alterno sobre evoluciones).
 *
 * <p>Cuándo se aplica (decisión del equipo): al crear la carta, DENTRO de la transacción, después de
 * obtener el código que va a recibir y antes de guardarla. Así se compara contra el código real; si
 * la regla falla, la transacción se revierte y el consecutivo no se consume.
 *
 * <p>Cómo se detecta un ciclo: se sigue la cadena desde el destino de la habilidad (la carta destino,
 * la carta a la que evoluciona esa, y así), usando {@code evolutionTargetOf}. Si la cadena vuelve al
 * código de la carta nueva, hay ciclo. Si la cadena se repite sin pasar por ella (un ciclo viejo
 * entre otras cartas) o termina, la carta nueva no crea ningún ciclo.
 */
public final class EvolutionRules {

    private EvolutionRules() {
    }

    /**
     * Comprueba la evolución de una carta nueva.
     *
     * @param newCard           código que va a recibir la carta
     * @param ability           habilidad que se le asigna
     * @param evolutionTargetOf para un código de carta existente, el destino de evolución de su habilidad
     * @throws ValidationException INVALID_EVOLUTION si evoluciona a sí misma o forma un ciclo
     */
    public static void check(CardCode newCard, Ability ability, Function<String, Optional<String>> evolutionTargetOf) {
        Optional<String> target = ability.evolutionTarget();
        if (target.isEmpty()) {
            return;
        }

        // (a) Evolución a sí misma.
        if (target.get().equals(newCard.value())) {
            throw ValidationException.invalidEvolution(CardFieldRules.ABILITY_FIELD,
                    "La habilidad " + ability.code() + " haría que la carta " + newCard
                            + " evolucione a sí misma.");
        }

        // (b) Ciclo: se recorre la cadena de evoluciones desde el destino.
        Set<String> visited = new HashSet<>();
        Optional<String> current = target;
        while (current.isPresent() && visited.add(current.get())) {
            Optional<String> next = evolutionTargetOf.apply(current.get());
            if (next.isPresent() && next.get().equals(newCard.value())) {
                throw ValidationException.invalidEvolution(CardFieldRules.ABILITY_FIELD,
                        "La habilidad " + ability.code() + " formaría un ciclo de evoluciones: " + newCard
                                + " evoluciona a " + target.get() + " y esa cadena vuelve a " + newCard + ".");
            }
            current = next;
        }
    }
}
