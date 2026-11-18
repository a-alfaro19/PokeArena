package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import cr.ac.tec.pokearena.domain.model.CatalogCriteria;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.repository.PlayerCatalogRepository;

/**
 * Propósito: implementación JDBC de {@link PlayerCatalogRepository}: el catálogo y la colección del
 * jugador (tablas card y player_card).
 * Responsabilidades: leer la página compartible de cartas disponibles y activas, la página de cartas
 * propias con sus copias y las copias de los códigos de una página. El mapeo de cada carta es el mismo de
 * la búsqueda del administrador ({@link CardRows}).
 * Referencia: sección 8.3 ("Cómo se resuelve") y Anexo A.6.
 */
@Repository
public class JdbcPlayerCatalogRepository implements PlayerCatalogRepository {

    private final JdbcTemplate jdbc;
    private final CardRows cardRows;

    public JdbcPlayerCatalogRepository(JdbcTemplate jdbc, CardRows cardRows) {
        this.jdbc = jdbc;
        this.cardRows = cardRows;
    }

    /** Catálogo compartible: mantiene la visibilidad del jugador dentro de la consulta paginada. */
    @Override
    public List<StoredCard> available(CatalogCriteria criteria, long afterId, int maxRows) {
        CardSearchSql.Query query = CardSearchSql.catalog(criteria.cardCriteria(), afterId, maxRows, null);
        return jdbc.query(query.sql(), (rs, row) -> cardRows.map(rs), query.arguments().toArray());
    }

    /** Colección privada: filtra antes del límite y obtiene las copias en la misma consulta. */
    @Override
    public List<OwnedCard> owned(UUID playerId, CatalogCriteria criteria, long afterId, int maxRows) {
        CardSearchSql.Query query = CardSearchSql.catalog(criteria.cardCriteria(), afterId, maxRows, playerId);
        return jdbc.query(query.sql(), (rs, row) -> new OwnedCard(cardRows.map(rs), rs.getInt("copies")),
                query.arguments().toArray());
    }

    /** Una consulta por página, no una por carta; no consulta nada para una página vacía. */
    @Override
    public Map<String, Integer> findCopies(UUID playerId, Collection<String> cardCodes) {
        if (cardCodes.isEmpty()) {
            return Map.of();
        }
        // Solo se genera la cantidad de placeholders; jugador y códigos siguen parametrizados.
        String placeholders = String.join(",", Collections.nCopies(cardCodes.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(playerId);
        arguments.addAll(cardCodes);
        Map<String, Integer> copies = new HashMap<>();
        jdbc.query("SELECT card_code, copies FROM player_card WHERE player_id = ? AND card_code IN ("
                + placeholders + ")", (RowCallbackHandler) rs ->
                        copies.put(rs.getString("card_code"), rs.getInt("copies")), arguments.toArray());
        return copies;
    }
}
