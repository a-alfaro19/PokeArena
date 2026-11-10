package cr.ac.tec.pokearena.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.CatalogCriteria;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.model.StoredCard;

/**
 * Propósito: lecturas del catálogo visible al jugador, separadas de la búsqueda administrativa.
 * Responsabilidades: filtrar cartas disponibles y activas, por id y con límite más uno (8.3, A.6).
 */
public interface PlayerCatalogRepository {

    /** Página compartible, sin identidad ni datos de colección. */
    List<StoredCard> available(CatalogCriteria criteria, long afterId, int maxRows);

    /** Copias del jugador para un lote de códigos; los ausentes representan cero copias. */
    Map<String, Integer> findCopies(UUID playerId, Collection<String> cardCodes);

    /** Página personal: unión a la colección del jugador verificado, sin caché. */
    List<OwnedCard> owned(UUID playerId, CatalogCriteria criteria, long afterId, int maxRows);
}
