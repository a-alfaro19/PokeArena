package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.model.CardCode;

/**
 * Propósito: pruebas unitarias de {@link EvolutionRules} (criterios de C.1 sobre evoluciones).
 * Responsabilidades: comprobar que se rechaza la evolución a la misma carta y los ciclos, directos
 * o a través de una cadena larga, y que se aceptan las cadenas sin ciclo.
 * Referencia: sección 7.1 ("EvolutionRules").
 *
 * <p>Las cartas existentes se representan con un mapa "carta -> destino de su habilidad".
 */
@DisplayName("Reglas de evolución")
class EvolutionRulesTest {

    // Longitud de la cadena larga de evoluciones.
    private static final int LONG_CHAIN = 200;

    private static final CardCode NEW_CARD = CardCode.of("FUE", 7);

    private final Map<String, String> evolutions = new HashMap<>();
    private final Function<String, Optional<String>> targetOf = code -> Optional.ofNullable(evolutions.get(code));

    private static Ability evolvingTo(String target) {
        return new Ability("HAB-EVO", "Evolución", null, target);
    }

    @Test
    @DisplayName("Una habilidad sin evolución siempre es válida")
    void abilityWithoutEvolution() {
        assertThatCode(() -> EvolutionRules.check(NEW_CARD, new Ability("HAB-000", "Sin Habilidad", null, null), targetOf))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una evolución hacia una carta sin más evoluciones es válida")
    void simpleEvolution() {
        assertThatCode(() -> EvolutionRules.check(NEW_CARD, evolvingTo("PKA-AGU-001"), targetOf))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una evolución cuyo destino es la misma carta es INVALID_EVOLUTION")
    void selfEvolutionIsRejected() {
        assertInvalid(evolvingTo(NEW_CARD.value()));
    }

    @Test
    @DisplayName("Un ciclo directo (A evoluciona a B y B a A) es INVALID_EVOLUTION")
    void directCycleIsRejected() {
        evolutions.put("PKA-AGU-001", NEW_CARD.value());

        assertInvalid(evolvingTo("PKA-AGU-001"));
    }

    @Test
    @DisplayName("Un ciclo a través de una cadena larga de evoluciones es INVALID_EVOLUTION")
    void longCycleIsRejected() {
        // PKA-AGU-001 -> PKA-AGU-002 -> ... -> PKA-AGU-200 -> carta nueva.
        for (int i = 1; i < LONG_CHAIN; i++) {
            evolutions.put(CardCode.of("AGU", i).value(), CardCode.of("AGU", i + 1).value());
        }
        evolutions.put(CardCode.of("AGU", LONG_CHAIN).value(), NEW_CARD.value());

        assertInvalid(evolvingTo(CardCode.of("AGU", 1).value()));
    }

    @Test
    @DisplayName("Una cadena larga que termina sin volver a la carta nueva es válida")
    void longChainWithoutCycle() {
        for (int i = 1; i < LONG_CHAIN; i++) {
            evolutions.put(CardCode.of("AGU", i).value(), CardCode.of("AGU", i + 1).value());
        }

        assertThatCode(() -> EvolutionRules.check(NEW_CARD, evolvingTo(CardCode.of("AGU", 1).value()), targetOf))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un ciclo viejo entre otras cartas no traba la regla ni se atribuye a la carta nueva")
    void existingCycleElsewhereTerminates() {
        evolutions.put("PKA-AGU-001", "PKA-AGU-002");
        evolutions.put("PKA-AGU-002", "PKA-AGU-001");

        assertThatCode(() -> EvolutionRules.check(NEW_CARD, evolvingTo("PKA-AGU-001"), targetOf))
                .doesNotThrowAnyException();
    }

    private void assertInvalid(Ability ability) {
        assertThatThrownBy(() -> EvolutionRules.check(NEW_CARD, ability, targetOf))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.type()).isEqualTo(ErrorType.INVALID_EVOLUTION);
                    org.assertj.core.api.Assertions.assertThat(error.field()).contains("ability");
                });
    }
}
