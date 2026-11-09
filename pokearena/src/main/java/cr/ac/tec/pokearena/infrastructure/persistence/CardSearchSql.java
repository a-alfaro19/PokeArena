package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 *       (portado del código original del Sprint 1, que usaba {@code !}).</li>
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

    private static final String COLUMNS = """
            c.id, c.code, c.name, c.name_normalized, c.cost, c.power,
                   c.type_code, t.name AS type_name, c.rarity_code, r.name AS rarity_name,
                   c.image_large_url, c.image_small_url, c.image_status, c.available, c.created_at,
                   """ + AbilityRows.COLUMNS;

    private static final String FROM = """

            FROM card c
            JOIN card_type t ON t.code = c.type_code
            JOIN rarity r ON r.code = c.rarity_code
            JOIN ability a ON a.code = c.ability_code
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
        return build(criteria, afterId, maxRows, false, null);
    }

    /** Reutiliza filtros y proyección; la visibilidad del jugador nunca cambia la búsqueda administrativa. */
    public static Query catalog(CardSearchCriteria criteria, long afterId, int maxRows, UUID owner) {
        return build(criteria, afterId, maxRows, true, owner);
    }

    private static Query build(CardSearchCriteria criteria, long afterId, int maxRows,
            boolean availableOnly, UUID owner) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (owner != null) {
            sql.append("pc.copies, ");
        }
        sql.append(COLUMNS).append(FROM);
        List<Object> arguments = new ArrayList<>();
        if (owner != null) {
            // La clave de player_card garantiza una fila por carta; la unión obtiene también sus copias.
            sql.append("JOIN player_card pc ON pc.card_code = c.code AND pc.player_id = ?\n");
            arguments.add(owner);
        }
        sql.append("WHERE c.id > ?\n");
        arguments.add(afterId);
        if (availableOnly) {
            // available ya exige READY en el modelo; active excluye las cartas desactivadas.
            sql.append("  AND c.available AND c.active\n");
        }

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
