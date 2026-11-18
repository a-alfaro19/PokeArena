package cr.ac.tec.pokearena.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.rule.QueryParameters.IntegerProblem;
import cr.ac.tec.pokearena.domain.rule.QueryParameters.ValueProblem;

/**
 * Propósito: pruebas unitarias de la lectura común de parámetros de consulta (secciones 7.3 y 8.3).
 * Responsabilidades: verificar nombres desconocidos en orden alfabético, valores repetidos o vacíos,
 * enteros acotados y rangos invertidos, sin depender del tipo de error de cada historia.
 * Referencia: secciones 7.3 y 8.3 (corrección 7).
 */
@DisplayName("Lectura común de parámetros de consulta")
class QueryParametersTest {

    @Test
    @DisplayName("El primer desconocido es el primero en orden alfabético")
    void firstUnknownIsAlphabetical() {
        Map<String, List<String>> parameters = Map.of("zeta", List.of("1"), "alfa", List.of("1"), "type", List.of("FUE"));

        assertThat(QueryParameters.firstUnknown(parameters, List.of("type"))).contains("alfa");
        assertThat(QueryParameters.firstUnknown(Map.of("type", List.of("FUE")), List.of("type"))).isEmpty();
    }

    @Test
    @DisplayName("Repetido antes que vacío; un solo valor con texto no tiene problema")
    void valueProblems() {
        assertThat(QueryParameters.valueProblem(List.of("1", "2"))).contains(ValueProblem.REPEATED);
        assertThat(QueryParameters.valueProblem(List.of("  "))).contains(ValueProblem.BLANK);
        assertThat(QueryParameters.valueProblem(List.of())).contains(ValueProblem.BLANK);
        assertThat(QueryParameters.valueProblem(Arrays.asList((String) null))).contains(ValueProblem.BLANK);
        assertThat(QueryParameters.valueProblem(null)).contains(ValueProblem.BLANK);
        assertThat(QueryParameters.valueProblem(List.of("3"))).isEmpty();
    }

    @Test
    @DisplayName("Entero acotado: ausente es null, con espacios se acepta, y cada problema se informa a quien llama")
    void boundedInteger() {
        Map<String, List<String>> parameters = Map.of("ok", List.of(" 3 "), "text", List.of("1.5"), "high", List.of("11"));

        assertThat(QueryParameters.boundedInteger(parameters, "missing", 0, 10, QueryParametersTest::error)).isNull();
        assertThat(QueryParameters.boundedInteger(parameters, "ok", 0, 10, QueryParametersTest::error)).isEqualTo(3);
        assertThatThrownBy(() -> QueryParameters.boundedInteger(parameters, "text", 0, 10, QueryParametersTest::error))
                .hasMessage(IntegerProblem.NOT_AN_INTEGER.name());
        assertThatThrownBy(() -> QueryParameters.boundedInteger(parameters, "high", 0, 10, QueryParametersTest::error))
                .hasMessage(IntegerProblem.OUT_OF_RANGE.name());
    }

    @Test
    @DisplayName("Rango invertido solo si están los dos extremos y el mínimo es mayor")
    void inverted() {
        assertThat(QueryParameters.inverted(5, 1)).isTrue();
        assertThat(QueryParameters.inverted(1, 1)).isFalse();
        assertThat(QueryParameters.inverted(null, 1)).isFalse();
        assertThat(QueryParameters.inverted(5, null)).isFalse();
    }

    @Test
    @DisplayName("Extremos de un rango: se leen el mínimo y luego el máximo, cada problema con su parámetro; el orden lo decide quien llama")
    void bounds() {
        Map<String, List<String>> parameters = Map.of("costMin", List.of("5"), "costMax", List.of("1"));

        QueryParameters.Bounds bounds = QueryParameters.bounds(parameters, "costMin", "costMax", 0, 10,
                (name, problem) -> ValidationException.invalidField(name, problem.name(), null));
        assertThat(bounds).isEqualTo(new QueryParameters.Bounds(5, 1));
        assertThat(bounds.inverted()).isTrue();
        assertThat(QueryParameters.bounds(Map.of(), "costMin", "costMax", 0, 10, (name, problem) -> null))
                .isEqualTo(new QueryParameters.Bounds(null, null));
        assertThatThrownBy(() -> QueryParameters.bounds(Map.of("costMin", List.of("x"), "costMax", List.of("99")),
                "costMin", "costMax", 0, 10, (name, problem) -> ValidationException.invalidField(name, problem.name(), null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).contains("costMin"));
    }

    @Test
    @DisplayName("Código de catálogo: ausente es null, existente se devuelve y uno inexistente lo informa a quien llama")
    void existingCode() {
        Map<String, List<String>> parameters = Map.of("type", List.of(" FUE "), "rarity", List.of("XXX"));

        assertThat(QueryParameters.existingCode(parameters, "missing", List.of("FUE"), QueryParametersTest::unknown))
                .isNull();
        assertThat(QueryParameters.existingCode(parameters, "type", List.of("FUE"), QueryParametersTest::unknown))
                .isEqualTo("FUE");
        assertThatThrownBy(() -> QueryParameters.existingCode(parameters, "rarity", List.of("COMMON"),
                QueryParametersTest::unknown)).hasMessage("XXX");
    }

    private static ValidationException unknown(String value) {
        return ValidationException.invalidField("campo", value, null);
    }

    private static ValidationException error(IntegerProblem problem) {
        return ValidationException.invalidField("campo", problem.name(), null);
    }
}
