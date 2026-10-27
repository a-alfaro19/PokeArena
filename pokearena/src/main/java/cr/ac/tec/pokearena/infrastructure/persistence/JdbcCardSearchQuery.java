package cr.ac.tec.pokearena.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import cr.ac.tec.pokearena.application.dto.CardSearchItem;
import cr.ac.tec.pokearena.application.dto.CardSearchPage;
import cr.ac.tec.pokearena.application.port.CardSearchQuery;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.CardSearchException;
import cr.ac.tec.pokearena.domain.model.CardSearchException.Reason;

@Repository
public class JdbcCardSearchQuery implements CardSearchQuery {
    private static final String CATALOG_SELECT = """
                SELECT c.code,
                    jsonb_build_object('code', c.code, 'name', c.name,
                        'largeImageUrl', c.large_image_url, 'smallImageUrl', c.small_image_url,
                        'imageStatus', c.image_status, 'cost', c.cost, 'power', c.power,
                        'type', c.type, 'rarity', c.rarity,
                        'ability', jsonb_build_object('code', c.ability_code,
                            'name', CASE WHEN c.ability_code = 'HAB-000' THEN 'Sin Habilidad' ELSE a.name END,
                            'trigger', CASE WHEN c.ability_code <> 'HAB-000' THEN a.trigger_code END,
                            'effect', CASE WHEN c.ability_code <> 'HAB-000' THEN a.effect_code END,
                            'parameters', CASE WHEN c.ability_code = 'HAB-000' THEN '{}'::jsonb
                                ELSE COALESCE(a.parameters, '{}'::jsonb) END))::text AS payload
                FROM cards c LEFT JOIN abilities a ON a.code = c.ability_code
                WHERE TRUE
                """;
    private static final int MAX_ROWS = 20000;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();

    public JdbcCardSearchQuery(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setTimeout(6);
    }

    @Override
    public CardSearchPage search(CardSearchCriteria criteria, int pageSize, UUID cursor, String subject) {
        try {
            return transaction.execute(status -> {
                // PostgreSQL limita cada sentencia incluso cuando el cliente abandona la solicitud.
                jdbc.execute("SET LOCAL statement_timeout = '5s'");
                if (cursor == null) {
                    return initialPage(criteria, pageSize, subject);
                }
                var pages = jdbc.query("SELECT search_id, position FROM card_search_cursors WHERE token = ?",
                        (rs, row) -> new CursorPosition(rs.getObject(1, UUID.class), rs.getInt(2)), cursor);
                if (pages.isEmpty()) { throw expired(); }
                var position = pages.getFirst();
                return readPage(position.searchId(), position.position(), criteria, pageSize, subject);
            });
        } catch (DataAccessException exception) {
            // No expone SQL, datos ni detalles del almacenamiento al consumidor.
            throw new CardSearchException(Reason.UNAVAILABLE, "search", "Búsqueda temporalmente no disponible");
        }
    }

    private CardSearchPage initialPage(CardSearchCriteria criteria, int size, String subject) {
        // El sondeo lee como máximo una página más una fila, sin reservar un recorrido.
        List<Object> parameters = new ArrayList<>();
        String conditions = buildConditions(criteria.values(), parameters);
        parameters.add(size + 1);
        String sql = "SELECT CASE WHEN octet_length(payload) <= 16384 THEN payload END FROM ("
                + CATALOG_SELECT + conditions + " ORDER BY c.code COLLATE \"C\" LIMIT ?) preview";
        List<String> payloads = jdbc.query(sql, (rs, row) -> rs.getString(1), parameters.toArray());
        if (payloads.stream().anyMatch(java.util.Objects::isNull)) { throw unavailable(); }
        if (payloads.size() <= size) {
            // Sin cursor no se necesitan reintentos de páginas: no se escribe estado temporal.
            var items = payloads.stream().map(payload -> json.readValue(payload, CardSearchItem.class)).toList();
            var expires = jdbc.queryForObject("SELECT clock_timestamp() + INTERVAL '10 minutes'", Timestamp.class);
            return new CardSearchPage(items, items.size(), expires.toInstant(), null);
        }
        // La sentencia de materialización, no el sondeo, fija el conjunto de un recorrido paginado.
        return createSnapshot(criteria, size, subject);
    }

    private CardSearchPage createSnapshot(CardSearchCriteria criteria, int size, String subject) {
        // Serializa solo la admisión; rechaza contención para no acumular búsquedas en espera.
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(390039)", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) { throw unavailable(); }
        jdbc.update("DELETE FROM card_search_snapshots WHERE expires_at <= clock_timestamp()");
        Integer active = jdbc.queryForObject("SELECT count(*) FROM card_search_snapshots", Integer.class);
        if (active != null && active >= 20) { throw unavailable(); }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO card_search_snapshots(id, owner_id, filters, page_size) VALUES (?, ?, ?, ?)",
                id, subject, criteria.canonical(), size);

        // Una sola sentencia MVCC fija el conjunto confirmado y copia los metadatos de la habilidad.
        List<Object> parameters = new ArrayList<>();
        parameters.add(id);
        String conditions = buildConditions(criteria.values(), parameters);
        String sql = "INSERT INTO card_search_results(search_id, position, payload) "
                + "SELECT ?, row_number() OVER (ORDER BY selected.code COLLATE \"C\"), selected.payload FROM ("
                + CATALOG_SELECT + conditions
                + " ORDER BY c.code COLLATE \"C\" LIMIT 20001) selected";
        jdbc.update(sql, parameters.toArray());
        var totals = jdbc.queryForMap("SELECT count(*) AS total, COALESCE(sum(octet_length(payload)), 0) AS bytes FROM card_search_results WHERE search_id = ?", id);
        int total = ((Number) totals.get("total")).intValue();
        long bytes = ((Number) totals.get("bytes")).longValue();
        Long used = jdbc.queryForObject("SELECT COALESCE(sum(payload_bytes), 0) FROM card_search_snapshots", Long.class);
        if (total > MAX_ROWS || bytes + (used == null ? 0 : used) > MAX_BYTES) { throw unavailable(); }
        jdbc.update("UPDATE card_search_snapshots SET total = ?, payload_bytes = ? WHERE id = ?", total, bytes, id);
        return readPage(id, 0, criteria, size, subject);
    }

    private String buildConditions(Map<String, String> filters, List<Object> parameters) {
        // Solo columnas y operadores controlados se concatenan; todos los valores son parámetros.
        StringBuilder sql = new StringBuilder();
        for (var filter : filters.entrySet()) {
            String key = filter.getKey();
            String value = filter.getValue();
            if ("name".equals(key)) {
                sql.append(" AND public.us39_normalize(c.name) LIKE ('%' || replace(replace(replace(public.us39_normalize(?), '!', '!!'), '%', '!%'), '_', '!_') || '%') ESCAPE '!' ");
                parameters.add(value);
                continue;
            }
            String column = switch (key) {
                case "code" -> "c.code";
                case "type" -> "c.type";
                case "rarity" -> "c.rarity";
                case "abilityCode" -> "c.ability_code";
                case "abilityTrigger" -> "a.trigger_code";
                case "abilityEffect" -> "a.effect_code";
                case "cost", "costMin", "costMax" -> "c.cost";
                case "power", "powerMin", "powerMax" -> "c.power";
                default -> throw CardSearchException.invalid(key, "Criterio desconocido");
            };
            String operator = key.endsWith("Min") ? ">=" : key.endsWith("Max") ? "<=" : "=";
            sql.append(" AND ").append(column).append(' ').append(operator).append(" ?");
            boolean numeric = key.startsWith("cost") || key.startsWith("power");
            parameters.add(numeric ? Integer.valueOf(value) : value);
            if ("abilityTrigger".equals(key) || "abilityEffect".equals(key)) {
                sql.append(" AND c.ability_code <> 'HAB-000'");
            }
        }
        return sql.toString();
    }

    private CardSearchPage readPage(UUID id, int position, CardSearchCriteria criteria, int size, String subject) {
        // El bloqueo compartido evita que la limpieza retire el conjunto a mitad de la página.
        var snapshots = jdbc.query("SELECT owner_id, filters, page_size, total, expires_at, expires_at > clock_timestamp() AS valid FROM card_search_snapshots WHERE id = ? FOR SHARE",
                (rs, row) -> new Snapshot(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        rs.getTimestamp(5), rs.getBoolean(6)), id);
        if (snapshots.isEmpty() || !snapshots.getFirst().valid()) { throw expired(); }
        var snapshot = snapshots.getFirst();
        if (!snapshot.owner().equals(subject)) {
            throw new CardSearchException(Reason.FORBIDDEN, "cursor", "El cursor pertenece a otro usuario");
        }
        if (!snapshot.filters().equals(criteria.canonical()) || snapshot.size() != size) {
            throw CardSearchException.invalid("cursor", "El cursor no corresponde a los filtros o tamaño");
        }
        List<CardSearchItem> items = jdbc.query("SELECT payload FROM card_search_results WHERE search_id = ? AND position > ? ORDER BY position LIMIT ?",
                (rs, row) -> json.readValue(rs.getString(1), CardSearchItem.class), id, position, size);
        UUID next = null;
        int nextPosition = position + items.size();
        if (nextPosition < snapshot.total()) {
            // Un reintento de la página obtiene el mismo token y no crea estado adicional.
            next = jdbc.queryForObject("INSERT INTO card_search_cursors(token, search_id, position) VALUES (?, ?, ?) ON CONFLICT (search_id, position) DO UPDATE SET position = EXCLUDED.position RETURNING token",
                    UUID.class, UUID.randomUUID(), id, nextPosition);
        }
        return new CardSearchPage(items, snapshot.total(), snapshot.expires().toInstant(), next);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void removeExpiredSnapshots() {
        // La limpieza periódica mantiene el límite temporal aunque no lleguen solicitudes nuevas.
        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL statement_timeout = '5s'");
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(390039)", Boolean.class))) {
                jdbc.update("DELETE FROM card_search_snapshots WHERE expires_at <= clock_timestamp()");
            }
        });
    }

    private static CardSearchException expired() {
        return new CardSearchException(Reason.EXPIRED, "cursor", "Cursor expirado o desconocido; inicie otra búsqueda");
    }

    private static CardSearchException unavailable() {
        return new CardSearchException(Reason.UNAVAILABLE, "search", "Capacidad temporal de búsqueda agotada");
    }

    // Estas proyecciones internas no salen del adaptador de almacenamiento.
    private record CursorPosition(UUID searchId, int position) { }
    private record Snapshot(String owner, String filters, int size, int total, Timestamp expires, boolean valid) { }
}
