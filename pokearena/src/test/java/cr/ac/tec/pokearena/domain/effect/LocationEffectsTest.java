package cr.ac.tec.pokearena.domain.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.LocationEffect;

/**
 * Propósito: pruebas unitarias de los plug-ins de efecto y de su registro (criterio C.2; sección 7.2).
 * Responsabilidades: comprobar las reglas de cada tipo de efecto, que el registro rechaza los tipos
 * inexistentes y que agregar un efecto nuevo es agregar una clase, sin modificar el registro ni el
 * caso de uso (abierto/cerrado).
 * Referencia: sección 7.2 ("Plug-ins de efecto") y sección 4.4 (Strategy y Registry).
 */
@DisplayName("Efectos de ubicación (plug-ins)")
class LocationEffectsTest {

    private static final List<String> TYPES = List.of("AGU", "FUE");

    private final LocationEffectRegistry registry = new LocationEffectRegistry(List.of(
            new TypeBonusEffect(() -> TYPES), new CostModifierEffect(() -> TYPES), new TurnRestrictionEffect()));

    private void assertInvalid(String code, Map<String, Object> parameters) {
        assertThatThrownBy(() -> registry.validate(code, parameters))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.INVALID_EFFECT);
                    assertThat(error.field()).contains("effect");
                });
    }

    @Nested
    @DisplayName("Bonificación por tipo (TYPE_BONUS)")
    class TypeBonus {

        @Test
        @DisplayName("Con un tipo existente y un monto entero de -5 a +5 es válida")
        void valid() {
            assertThat(registry.validate("TYPE_BONUS", Map.of("type", "FUE", "amount", 5))).isPresent();
            assertThat(registry.validate("TYPE_BONUS", Map.of("type", "AGU", "amount", -5))).isPresent();
        }

        @Test
        @DisplayName("Exige un tipo existente y un monto: sin ellos, o con otro tipo, es INVALID_EFFECT")
        void requiresExistingTypeAndAmount() {
            assertInvalid("TYPE_BONUS", Map.of("amount", 3));
            assertInvalid("TYPE_BONUS", Map.of("type", "FUE"));
            assertInvalid("TYPE_BONUS", Map.of("type", "XXX", "amount", 3));
        }

        @Test
        @DisplayName("Un monto fuera de -5 a +5, con decimales o como texto es INVALID_EFFECT con el rango")
        void amountMustBeIntegerInRange() {
            assertInvalid("TYPE_BONUS", Map.of("type", "FUE", "amount", 6));
            assertInvalid("TYPE_BONUS", Map.of("type", "FUE", "amount", 3.5));
            assertInvalid("TYPE_BONUS", Map.of("type", "FUE", "amount", "3"));
            assertThatThrownBy(() -> registry.validate("TYPE_BONUS", Map.of("type", "FUE", "amount", -6)))
                    .isInstanceOfSatisfying(ValidationException.class,
                            error -> assertThat(error.allowedValues()).containsExactly("-5..5"));
        }

        @Test
        @DisplayName("Un parámetro desconocido es INVALID_EFFECT, para detectar errores de escritura")
        void unknownParameter() {
            assertInvalid("TYPE_BONUS", Map.of("type", "FUE", "amount", 1, "amout", 1));
        }
    }

    @Nested
    @DisplayName("Modificador de costo (COST_MODIFIER)")
    class CostModifier {

        @Test
        @DisplayName("Con un monto de -5 a +5 es válido, con o sin tipo afectado")
        void valid() {
            assertThat(registry.validate("COST_MODIFIER", Map.of("amount", -2))).isPresent();
            assertThat(registry.validate("COST_MODIFIER", Map.of("amount", 2, "affectedType", "AGU"))).isPresent();
        }

        @Test
        @DisplayName("Sin monto, con monto fuera de rango o con un tipo afectado inexistente es INVALID_EFFECT")
        void invalid() {
            assertInvalid("COST_MODIFIER", Map.of());
            assertInvalid("COST_MODIFIER", Map.of("amount", 9));
            assertInvalid("COST_MODIFIER", Map.of("amount", 1, "affectedType", "XXX"));
        }
    }

    @Nested
    @DisplayName("Restricción por turno (TURN_RESTRICTION)")
    class TurnRestriction {

        @Test
        @DisplayName("Una lista no vacía de turnos de 1 a 6 sin repetidos es válida")
        void valid() {
            assertThat(registry.validate("TURN_RESTRICTION", Map.of("turns", List.of(1, 6)))).isPresent();
            assertThat(registry.validate("TURN_RESTRICTION", Map.of("turns", List.of(BigInteger.valueOf(3)))))
                    .isPresent();
        }

        @Test
        @DisplayName("Sin turnos, con la lista vacía, con turnos fuera de 1 a 6 o repetidos es INVALID_EFFECT")
        void invalid() {
            assertInvalid("TURN_RESTRICTION", Map.of());
            assertInvalid("TURN_RESTRICTION", Map.of("turns", List.of()));
            assertInvalid("TURN_RESTRICTION", Map.of("turns", List.of(0, 2)));
            assertInvalid("TURN_RESTRICTION", Map.of("turns", List.of(7)));
            assertInvalid("TURN_RESTRICTION", Map.of("turns", List.of(2, 2)));
            assertInvalid("TURN_RESTRICTION", Map.of("turns", 2));
        }
    }

    @Nested
    @DisplayName("Registro de efectos")
    class Registry {

        @Test
        @DisplayName("El efecto es opcional: sin código no hay efecto")
        void effectIsOptional() {
            assertThat(registry.validate(null, null)).isEmpty();
            assertThat(registry.validate(" ", Map.of())).isEmpty();
        }

        @Test
        @DisplayName("Un tipo de efecto inexistente es INVALID_EFFECT con los tipos registrados como valores permitidos")
        void unknownEffect() {
            assertThatThrownBy(() -> registry.validate("MAGIC_FIELD", Map.of()))
                    .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.allowedValues())
                            .containsExactly("COST_MODIFIER", "TURN_RESTRICTION", "TYPE_BONUS"));
        }

        @Test
        @DisplayName("Parámetros sin tipo de efecto son INVALID_EFFECT")
        void parametersWithoutCode() {
            assertInvalid(null, Map.of("amount", 1));
        }

        @Test
        @DisplayName("Dos plug-ins con el mismo código son un error de configuración")
        void duplicateCode() {
            assertThatThrownBy(() -> new LocationEffectRegistry(List.of(new TurnRestrictionEffect(),
                    new TurnRestrictionEffect()))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Agregar un efecto nuevo es agregar una clase: el registro lo acepta sin modificarse")
        void newEffectWithoutModification() {
            LocationEffectRegistry extended = new LocationEffectRegistry(List.of(new TurnRestrictionEffect(),
                    new WeatherEffect()));

            assertThat(extended.validate("WEATHER", Map.of("kind", "lluvia")))
                    .contains(new LocationEffect("WEATHER", Map.of("kind", "lluvia")));
            assertThat(extended.codes()).contains("WEATHER");
        }
    }

    /** Plug-in solo de prueba: demuestra que un efecto nuevo no requiere tocar el registro. */
    static final class WeatherEffect implements LocationEffectType {
        @Override
        public String code() {
            return "WEATHER";
        }

        @Override
        public void validate(Map<String, Object> parameters) {
            if (!(parameters.get("kind") instanceof String)) {
                throw ValidationException.invalidEffect("effect", "El clima necesita 'kind'.", List.of());
            }
        }
    }
}
