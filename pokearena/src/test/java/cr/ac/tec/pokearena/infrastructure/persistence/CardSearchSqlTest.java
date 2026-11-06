package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;

/**
 * Propósito: pruebas unitarias del armado de la consulta de búsqueda.
 * Responsabilidades: comprobar el escape de {@code %} y {@code _} (portado del código original del
 * Sprint 1) y que los valores del usuario van como parámetros, nunca dentro del texto SQL.
 * Referencia: sección 7.3 ("escapar % y _") y sección 5.5.
 */
@DisplayName("Consulta SQL de la búsqueda de cartas")
class CardSearchSqlTest {

    @Test
    @DisplayName("% y _ y el carácter de escape se escapan para buscarse como texto literal")
    void escapesWildcards() {
        assertThat(CardSearchSql.escapeLike("100%_volt\\x")).isEqualTo("100\\%\\_volt\\\\x");
    }

    @Test
    @DisplayName("Los valores van como parámetros; el SQL solo tiene el orden por id y el límite más uno")
    void valuesAreParameters() {
        CardSearchCriteria criteria = CardSearchCriteria.builder().nameFragment("' or 1=1 --").type("FUE")
                .costBetween(2, 2).build();

        CardSearchSql.Query query = CardSearchSql.build(criteria, 7, 21);

        assertThat(query.sql()).doesNotContain("or 1=1").contains("ORDER BY c.id").endsWith("LIMIT ?");
        assertThat(query.arguments()).containsExactly(7L, "%' or 1=1 --%", 2, 2, "FUE", 21);
    }

    @Test
    @DisplayName("Sin criterios solo filtra por el cursor")
    void noCriteria() {
        CardSearchSql.Query query = CardSearchSql.build(CardSearchCriteria.builder().build(), 0, 21);

        assertThat(query.sql()).doesNotContain(" AND ");
        assertThat(query.arguments()).containsExactly(0L, 21);
    }
}
