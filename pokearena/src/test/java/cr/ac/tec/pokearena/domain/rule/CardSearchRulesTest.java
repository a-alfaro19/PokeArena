package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;

/**
 * Propósito: pruebas unitarias de la validación de criterios de búsqueda (historia 2.1.2).
 * Responsabilidades: comprobar que cada criterio válido se convierte bien y que cada criterio inválido
 * es INVALID_SEARCH_CRITERIA con el criterio en {@code field}. Los casos se portaron de
 * {@code CardSearchValidationTests} del código original del Sprint 1 (etiqueta
 * {@code sprint1-original}) con los nombres y errores de la especificación.
 * Referencia: sección 7.3 y criterio C.3 (errores).
 */
@DisplayName("Reglas de los criterios de búsqueda de cartas")
class CardSearchRulesTest {

    private static final List<CardType> TYPES = List.of(new CardType("AGU", "Agua"), new CardType("FUE", "Fuego"));
    private static final List<Rarity> RARITIES = List.of(new Rarity("COMMON", "Común"), new Rarity("RARE", "Rara"));

    /** Parámetros con un solo valor cada uno: "nombre", "valor", "nombre", "valor", ... */
    private static Map<String, List<String>> params(String... pairs) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            parameters.put(pairs[i], List.of(pairs[i + 1]));
        }
        return parameters;
    }

    private static CardSearchCriteria validate(Map<String, List<String>> parameters) {
        return CardSearchRules.validate(parameters, TYPES, RARITIES);
    }

    private static void assertInvalid(Map<String, List<String>> parameters, String criterion) {
        assertThatThrownBy(() -> validate(parameters)).isInstanceOfSatisfying(ValidationException.class, error -> {
            assertThat(error.type()).isEqualTo(ErrorType.INVALID_SEARCH_CRITERIA);
            assertThat(error.field()).contains(criterion);
        });
    }

    @Test
    @DisplayName("Sin criterios no filtra nada: recorre todo el catálogo")
    void noCriteria() {
        assertThat(validate(Map.of()).asMap()).isEmpty();
    }

    @Test
    @DisplayName("Todos los criterios válidos se convierten; el nombre queda en minúsculas y sin tildes")
    void allCriteria() {
        CardSearchCriteria criteria = validate(params("code", "PKA-FUE-001", "name", " DRAGÓN ", "costMin", "2",
                "costMax", "5", "power", "-3", "type", "FUE", "rarity", "RARE", "ability", "HAB-000",
                "abilityTrigger", "AL_REVELAR", "abilityEffect", "BONUS"));

        assertThat(criteria.code()).contains("PKA-FUE-001");
        assertThat(criteria.nameFragment()).contains("dragon");
        assertThat(criteria.costMin()).contains(2);
        assertThat(criteria.costMax()).contains(5);
        assertThat(criteria.powerMin()).contains(-3);
        assertThat(criteria.powerMax()).contains(-3);
        assertThat(criteria.typeCode()).contains("FUE");
        assertThat(criteria.rarityCode()).contains("RARE");
        assertThat(criteria.abilityCode()).contains("HAB-000");
        assertThat(criteria.abilityTrigger()).contains("AL_REVELAR");
        assertThat(criteria.abilityEffect()).contains("BONUS");
    }

    @Test
    @DisplayName("Un valor exacto es el rango de un solo valor: cost=3 equivale a costMin=3&costMax=3")
    void exactValueIsSingleValueRange() {
        assertThat(validate(params("cost", "3")).asMap())
                .isEqualTo(validate(params("costMin", "3", "costMax", "3")).asMap());
    }

    @Test
    @DisplayName("Los rangos abiertos son válidos: solo mínimo o solo máximo")
    void openRanges() {
        assertThat(validate(params("costMin", "0")).costMax()).isEmpty();
        assertThat(validate(params("powerMax", "20")).powerMin()).isEmpty();
    }

    @Test
    @DisplayName("Los extremos permitidos se aceptan: costo 0 y 10, poder -20 y 20")
    void boundaries() {
        assertThat(validate(params("costMin", "0", "costMax", "10", "powerMin", "-20", "powerMax", "20")).asMap())
                .containsEntry("costMin", "0").containsEntry("powerMax", "20");
    }

    @ParameterizedTest(name = "{0}={1}: criterio {2}")
    @CsvSource({ "type, FIRE, type", "rarity, MYTHIC, rarity", "cost, -1, cost", "cost, 11, cost",
            "power, -21, power", "power, 21, power", "cost, 1.5, cost", "power, abc, power", "costMin, 99, costMin",
            "powerMax, -99, powerMax", "unknown, 1, unknown", "abilityCode, HAB-000, abilityCode" })
    @DisplayName("C.3 error: tipo o rareza inexistentes, valores fuera de rango o no enteros y criterios desconocidos son INVALID_SEARCH_CRITERIA con el criterio")
    void invalidCriterion(String name, String value, String criterion) {
        assertInvalid(params(name, value), criterion);
    }

    @Test
    @DisplayName("Un tipo o una rareza inexistentes informan los valores del catálogo")
    void catalogAllowedValues() {
        assertThatThrownBy(() -> validate(params("type", "FIRE")))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.allowedValues()).containsExactly("AGU", "FUE"));
        assertThatThrownBy(() -> validate(params("cost", "11")))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.allowedValues()).containsExactly("0..10"));
    }

    @ParameterizedTest(name = "{0} con {1}")
    @CsvSource({ "cost, costMin", "cost, costMax", "power, powerMin", "power, powerMax" })
    @DisplayName("Un valor exacto combinado con un rango del mismo campo se rechaza indicando el campo")
    void exactWithRange(String exact, String bound) {
        assertInvalid(params(exact, "1", bound, "2"), exact);
    }

    @ParameterizedTest(name = "{0}: mínimo mayor que máximo")
    @CsvSource({ "cost", "power" })
    @DisplayName("C.3 error: un rango con mínimo mayor que máximo se rechaza indicando el criterio")
    void reversedRange(String field) {
        assertInvalid(params(field + "Min", "2", field + "Max", "1"), field);
    }

    @Test
    @DisplayName("Un criterio repetido o vacío se rechaza indicando el criterio")
    void repeatedOrBlank() {
        assertInvalid(Map.of("type", Arrays.asList("FUE", "AGU")), "type");
        assertInvalid(params("name", ""), "name");
        assertInvalid(params("code", "   "), "code");
    }

    @Test
    @DisplayName("Con varios criterios desconocidos se informa siempre el primero en orden alfabético")
    void unknownOrderIsStable() {
        assertInvalid(params("zeta", "1", "alfa", "1", "type", "FUE"), "alfa");
    }

    @Test
    @DisplayName("El nombre conserva % y _ como texto: el escape es de la consulta SQL, no del dominio")
    void nameKeepsWildcards() {
        assertThat(validate(params("name", "100%_Volt")).nameFragment()).contains("100%_volt");
    }
}
