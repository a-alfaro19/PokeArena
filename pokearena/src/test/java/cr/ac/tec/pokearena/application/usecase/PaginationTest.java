package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: pruebas unitarias de la paginación única (sección 5.5; corrección 8).
 * Responsabilidades: comprobar el tamaño por defecto y el máximo que vienen de afuera, los errores
 * INVALID_PAGINATION de {@code limit} y {@code cursor}, el cursor opaco y el armado de la página con
 * la fila extra.
 * Referencia: sección 5.5.
 */
@DisplayName("Paginación única por cursor")
class PaginationTest {

    // Valores de la prueba, distintos de los de application.properties a propósito: así se comprueba
    // que la paginación usa los que recibe y no tiene números fijos.
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 7;

    private final Pagination pagination = new Pagination(DEFAULT_LIMIT, MAX_LIMIT);

    private static Map<String, List<String>> params(String name, String value) {
        return Map.of(name, List.of(value));
    }

    private void assertInvalid(Map<String, List<String>> parameters, String field) {
        assertThatThrownBy(() -> pagination.parse(parameters)).isInstanceOfSatisfying(ValidationException.class,
                error -> {
                    assertThat(error.type()).isEqualTo(ErrorType.INVALID_PAGINATION);
                    assertThat(error.field()).contains(field);
                });
    }

    @Test
    @DisplayName("Sin limit ni cursor: primera página con el tamaño por defecto de la configuración")
    void defaults() {
        assertThat(pagination.parse(Map.of())).isEqualTo(new PageRequest(PageRequest.FROM_START, DEFAULT_LIMIT));
    }

    @Test
    @DisplayName("limit entre 1 y el máximo de la configuración se acepta")
    void limitWithinRange() {
        assertThat(pagination.parse(params("limit", "1")).limit()).isEqualTo(1);
        assertThat(pagination.parse(params("limit", String.valueOf(MAX_LIMIT))).limit()).isEqualTo(MAX_LIMIT);
    }

    @ParameterizedTest(name = "limit={0}")
    @ValueSource(strings = { "0", "-1", "8", "abc", "2.5", "" })
    @DisplayName("limit fuera de 1 a máximo, no entero o vacío es INVALID_PAGINATION con el rango permitido")
    void invalidLimit(String limit) {
        assertInvalid(params("limit", limit), "limit");
        if (!limit.isBlank()) {
            assertThatThrownBy(() -> pagination.parse(params("limit", limit)))
                    .isInstanceOfSatisfying(ValidationException.class,
                            error -> assertThat(error.allowedValues()).containsExactly("1.." + MAX_LIMIT));
        }
    }

    @Test
    @DisplayName("El cursor es el último id en base64url y se lee de vuelta")
    void cursorRoundTrip() {
        String cursor = Pagination.encode(42);

        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII)).isEqualTo("42");
        assertThat(pagination.parse(params("cursor", cursor)).afterId()).isEqualTo(42);
    }

    @ParameterizedTest(name = "cursor={0}")
    @ValueSource(strings = { "bad!", "abc", "LTE", "KzU", "MDU", "MTI=", "" })
    @DisplayName("Un cursor que no generó el servidor (no base64url, no numérico, negativo, +5, 05, con relleno o vacío) es INVALID_PAGINATION")
    void invalidCursor(String cursor) {
        // "LTE" es "-1", "KzU" es "+5", "MDU" es "05" y "MTI=" es "12" con relleno.
        assertInvalid(params("cursor", cursor), "cursor");
    }

    @Test
    @DisplayName("limit o cursor repetidos son INVALID_PAGINATION")
    void repeated() {
        assertInvalid(Map.of("limit", List.of("1", "2")), "limit");
        assertInvalid(Map.of("cursor", List.of(Pagination.encode(1), Pagination.encode(2))), "cursor");
    }

    @Test
    @DisplayName("Con la fila extra hay otra página: se recorta y el cursor apunta al último elemento devuelto")
    void pageWithMore() {
        PageRequest request = new PageRequest(PageRequest.FROM_START, 2);

        Page<Long> page = pagination.toPage(List.of(10L, 11L, 12L), request, Long::longValue);

        assertThat(page.items()).containsExactly(10L, 11L);
        assertThat(page.nextCursor()).isEqualTo(Pagination.encode(11));
        assertThat(page.limit()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin la fila extra es la última página: nextCursor es null; sin filas, la lista está vacía")
    void lastPage() {
        PageRequest request = new PageRequest(PageRequest.FROM_START, 2);

        assertThat(pagination.toPage(List.of(10L, 11L), request, Long::longValue).nextCursor()).isNull();
        assertThat(pagination.toPage(List.<Long>of(), request, Long::longValue).items()).isEmpty();
    }

    @Test
    @DisplayName("Una configuración sin sentido no arranca: defecto mayor que el máximo, o menor que 1")
    void invalidConfiguration() {
        assertThatThrownBy(() -> new Pagination(MAX_LIMIT + 1, MAX_LIMIT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pagination(0, MAX_LIMIT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Los criterios de búsqueda se separan de limit y cursor")
    void withoutPagination() {
        assertThat(Pagination.withoutPagination(Map.of("limit", List.of("1"), "cursor", List.of("x"),
                "type", List.of("FUE")))).containsOnlyKeys("type");
    }
}
