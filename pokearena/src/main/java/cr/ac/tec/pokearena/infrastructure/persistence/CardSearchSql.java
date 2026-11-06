package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;

/**
 * Propósito: armar la consulta SQL de la búsqueda de cartas (sección 7.3) con la paginación por cursor
 * (sección 5.5).
 * Responsabilidades: agregar una condición por cada criterio activo (AND), con todos los valores como
 * parámetros de la sentencia, y escapar {@code %} y {@code _} en la búsqueda por nombre.
 * Referencia: sección 7.3, sección 5.5 y sección 6 (índices de card).
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Seguridad: el SQL solo concatena textos fijos de esta clase; los valores del usuario van
 *       siempre como parámetros ({@code ?}), nunca dentro del texto.</li>
 *   <li>Nombre: {@code name_normalized LIKE '%parte%'} sobre el nombre ya normalizado por el dominio
 *       (minúsculas y sin tildes). Usa el índice trigram {@code idx_card_name_normalized_trgm}. El
 *       carácter de escape es {@code \}: {@code 100%_} busca el texto literal, no comodines
 *       (portado de legacy, que usaba {@code !}).</li>
 *   <li>Disparador y efecto: se comparan con las columnas de la habilidad, así {@code HAB-000} (sin
 *       disparador ni efecto) nunca coincide con un disparador o un efecto.</li>
 *   <li>Orden y paginación: {@code id > ? ORDER BY id LIMIT ?}. El {@code id} es la clave primaria,
 *       así que cada página recorre el índice {@code pk_card} desde el cursor.</li>
 *   <li>Es pública solo para que la prueba de rendimiento pueda pedir el plan ({@code EXPLAIN}) de la
 *       misma consulta que usa la aplicación.</li>
 * </ul>
 */
public final class CardSearchSql {

    /** Carácter de escape del LIKE. */
    private static final String ESCAPE = "\\";

    private static final String SELECT = """
            SELECT c.id, c.code, c.name, c.name_normalized, c.cost, c.power,
                   c.type_code, t.name AS type_name, c.rarity_code, r.name AS rarity_name,
                   c.image_large_url, c.image_small_url, c.image_status, c.available, c.created_at,
                   """ + AbilityRows.COLUMNS + """

            FROM card c
            JOIN card_type t ON t.code = c.type_code
            JOIN rarity r ON r.code = c.rarity_code
            JOIN ability a ON a.code = c.ability_code
            WHERE c.id > ?
            """;

    private CardSearchSql() {
    }

    /** Sentencia SQL y sus parámetros, en orden. */
    public record Query(String sql, List<Object> arguments) {

        public Query {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * Consulta de una página.
     *
     * @param afterId último {@code id} de la página anterior (0 al principio)
     * @param maxRows filas como máximo (el tamaño de página más uno)
     */
    public static Query build(CardSearchCriteria criteria, long afterId, int maxRows) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> arguments = new ArrayList<>();
        arguments.add(afterId);

        equal(sql, arguments, "c.code", criteria.code());
        criteria.nameFragment().ifPresent(fragment -> {
            sql.append("  AND c.name_normalized LIKE ? ESCAPE '").append(ESCAPE).append("'\n");
            arguments.add("%" + escapeLike(fragment) + "%");
        });
        compare(sql, arguments, "c.cost", ">=", criteria.costMin());
        compare(sql, arguments, "c.cost", "<=", criteria.costMax());
        compare(sql, arguments, "c.power", ">=", criteria.powerMin());
        compare(sql, arguments, "c.power", "<=", criteria.powerMax());
        equal(sql, arguments, "c.type_code", criteria.typeCode());
        equal(sql, arguments, "c.rarity_code", criteria.rarityCode());
        equal(sql, arguments, "c.ability_code", criteria.abilityCode());
        equal(sql, arguments, "a.trigger", criteria.abilityTrigger());
        equal(sql, arguments, "a.effect_type", criteria.abilityEffect());

        sql.append("ORDER BY c.id\nLIMIT ?");
        arguments.add(maxRows);
        return new Query(sql.toString(), arguments);
    }

    /**
     * Escapa los comodines del LIKE ({@code %} y {@code _}) y el propio carácter de escape, para que el
     * texto se busque literalmente.
     */
    static String escapeLike(String text) {
        return text.replace(ESCAPE, ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }

    private static void equal(StringBuilder sql, List<Object> arguments, String column, Optional<String> value) {
        compare(sql, arguments, column, "=", value);
    }

    private static void compare(StringBuilder sql, List<Object> arguments, String column, String operator,
            Optional<?> value) {
        value.ifPresent(present -> {
            sql.append("  AND ").append(column).append(' ').append(operator).append(" ?\n");
            arguments.add(present);
        });
    }
}
