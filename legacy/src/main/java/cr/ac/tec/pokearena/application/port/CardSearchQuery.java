package cr.ac.tec.pokearena.application.port;

import java.util.UUID;
import cr.ac.tec.pokearena.application.dto.CardSearchPage;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;

// La consulta no participa en creación, almacenamiento de imágenes ni mutaciones de cartas.
public interface CardSearchQuery {
    CardSearchPage search(CardSearchCriteria criteria, int pageSize, UUID cursor, String subject);
}
