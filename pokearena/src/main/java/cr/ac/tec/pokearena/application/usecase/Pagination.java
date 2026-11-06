package cr.ac.tec.pokearena.application.usecase;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.domain.error.ValidationException;

/**
 * Propósito: la única estrategia de paginación del sistema (corrección 8 del profesor; sección 5.5).
 * Responsabilidades: validar {@code limit} y {@code cursor} (INVALID_PAGINATION), y armar la página a
 * partir de las filas leídas: recortar la fila extra y calcular {@code nextCursor}. La usan la búsqueda
 * del administrador (7.3) y, más adelante, el catálogo del jugador (8.3).
 * Referencia: sección 5.5.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Cursor por clave: la página siguiente es {@code id > último id}, en orden de {@code id}. Como
 *       los {@code id} solo crecen, una carta creada durante el recorrido aparece al final y nunca
 *       desplaza a otra: no hay repetidos ni omitidos (criterio C.3).</li>
 *   <li>El cursor es opaco: el último {@code id} en base64url sin relleno. El cliente no debe armarlo;
 *       solo devuelve el que recibió. Se acepta únicamente la forma exacta que genera el servidor
 *       (decimal sin signo ni ceros a la izquierda), así cada posición tiene un solo cursor.</li>
 *   <li>El tamaño por defecto y el máximo vienen de configuración
 *       ({@code app.pagination.default-limit} y {@code app.pagination.max-limit}): ningún número fijo
 *       en el código.</li>
 * </ul>
 */
public final class Pagination {

    /** Nombres de los parámetros de la paginación, iguales en todas las rutas paginadas. */
    public static final String LIMIT = "limit";
    public static final String CURSOR = "cursor";
    public static final Set<String> PARAMETERS = Set.of(LIMIT, CURSOR);

    /** Tamaño mínimo de una página. */
    private static final int MIN_LIMIT = 1;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final int defaultLimit;
    private final int maxLimit;

    /**
     * @param defaultLimit tamaño cuando el cliente no indica {@code limit}
     * @param maxLimit     tamaño máximo permitido
     */
    public Pagination(int defaultLimit, int maxLimit) {
        if (maxLimit < MIN_LIMIT || defaultLimit < MIN_LIMIT || defaultLimit > maxLimit) {
            throw new IllegalArgumentException("La paginación necesita 1 <= default-limit <= max-limit; recibió "
                    + defaultLimit + " y " + maxLimit);
        }
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    /** Los parámetros de la solicitud sin {@code limit} ni {@code cursor}: los criterios de la búsqueda. */
    public static Map<String, List<String>> withoutPagination(Map<String, List<String>> parameters) {
        Map<String, List<String>> criteria = new HashMap<>(parameters);
        criteria.keySet().removeAll(PARAMETERS);
        return criteria;
    }

    /**
     * Valida {@code limit} y {@code cursor} de los parámetros de la solicitud.
     *
     * @throws ValidationException INVALID_PAGINATION con el parámetro en {@code field}
     */
    public PageRequest parse(Map<String, List<String>> parameters) {
        int limit = parseLimit(single(parameters, LIMIT));
        long afterId = parseCursor(single(parameters, CURSOR));
        return new PageRequest(afterId, limit);
    }

    /**
     * Arma la página con las filas leídas, que deben venir en orden de {@code id} y ser como máximo
     * {@link PageRequest#fetchSize()}. Si llegó la fila extra, hay otra página y el cursor apunta al
     * último elemento que se devuelve.
     */
    public <T> Page<T> toPage(List<T> rows, PageRequest request, ToLongFunction<T> idOf) {
        if (rows.size() <= request.limit()) {
            return new Page<>(rows, null, request.limit());
        }
        List<T> items = rows.subList(0, request.limit());
        return new Page<>(items, encode(idOf.applyAsLong(items.getLast())), request.limit());
    }

    /** Cursor opaco que apunta después del elemento con ese {@code id}. */
    public static String encode(long id) {
        return ENCODER.encodeToString(Long.toString(id).getBytes(StandardCharsets.US_ASCII));
    }

    // ---------------------------------------------------------------------------------------------

    private int parseLimit(String value) {
        if (value == null) {
            return defaultLimit;
        }
        List<String> allowed = List.of(MIN_LIMIT + ".." + maxLimit);
        try {
            int limit = Integer.parseInt(value.strip());
            if (limit >= MIN_LIMIT && limit <= maxLimit) {
                return limit;
            }
        } catch (NumberFormatException notAnInteger) {
            // Mismo error que fuera de rango: el cliente corrige con los valores permitidos.
        }
        throw ValidationException.invalidPagination(LIMIT,
                "El límite debe ser un número entero entre " + MIN_LIMIT + " y " + maxLimit + ".", allowed);
    }

    private static long parseCursor(String value) {
        if (value == null) {
            return PageRequest.FROM_START;
        }
        try {
            String decoded = new String(DECODER.decode(value), StandardCharsets.US_ASCII);
            long id = Long.parseLong(decoded);
            // Solo la forma que genera el servidor: así "+5", "05" o un relleno distinto no son cursores.
            if (id >= PageRequest.FROM_START && encode(id).equals(value)) {
                return id;
            }
        } catch (IllegalArgumentException notACursor) {
            // Base64 inválido o texto no numérico (NumberFormatException es una IllegalArgumentException).
        }
        throw ValidationException.invalidPagination(CURSOR,
                "El cursor no es válido: use el nextCursor de la página anterior.", null);
    }

    /** El único valor del parámetro, o {@code null} si no vino. Repetido o vacío es inválido. */
    private static String single(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
            throw ValidationException.invalidPagination(name,
                    "El parámetro '" + name + "' debe indicarse una sola vez y no puede estar vacío.", null);
        }
        return values.getFirst();
    }
}
