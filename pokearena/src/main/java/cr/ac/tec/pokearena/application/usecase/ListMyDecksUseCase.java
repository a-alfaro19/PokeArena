package cr.ac.tec.pokearena.application.usecase;

import java.util.List;

import cr.ac.tec.pokearena.application.dto.DeckSummary;
import cr.ac.tec.pokearena.application.dto.ListMyDecksCommand;
import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;

/**
 * Propósito: caso de uso "Listar mis decks" de la historia 3.1.3.
 * Responsabilidades: validar la paginación y devolver una página de los decks del jugador de la sesión,
 * ordenados por {@code id}.
 * Referencia: sección 7.1 ("Listar y consultar") y Anexo A.8, mensajes 1 a 14.
 *
 * <h2>Misma plantilla que los demás casos de uso</h2>
 * <ol>
 *   <li><b>Autorizar</b> (mensajes 3 y 4): en la API, {@code @RequiresRole({PLAYER, ADMIN})}.</li>
 *   <li><b>Idempotencia:</b> no aplica; es una lectura (GET).</li>
 *   <li><b>Validar</b> (mensajes 6 y 7): {@code limit} y {@code cursor} con {@link Pagination}, la misma de la
 *       búsqueda y el catálogo (sección 5.5).</li>
 *   <li><b>Ejecutar:</b> una lectura, sin {@code UnitOfWork} (sección 4.3: las lecturas no usan transacción
 *       explícita).</li>
 *   <li><b>Responder</b> (mensajes 12 y 13): 200 con la página.</li>
 * </ol>
 *
 * <p>Los decks no se cachean: son datos del jugador (sección 9).
 */
public class ListMyDecksUseCase {

    private final Pagination pagination;
    private final DeckRepository decks;

    public ListMyDecksUseCase(Pagination pagination, DeckRepository decks) {
        this.pagination = pagination;
        this.decks = decks;
    }

    /** Una página de los decks del jugador. */
    public Page<DeckSummary> execute(ListMyDecksCommand command) {
        // Mensajes 2 a 5 del diagrama A.8: la interfaz pidió GET /api/v1/decks, AuthorizationInterceptor verificó
        // el token y el rol, y DeckController llamó a este caso de uso.

        // Mensajes 6 y 7 del diagrama A.8: Pagination.parse valida limit y cursor (INVALID_PAGINATION).
        PageRequest page = pagination.parse(command.pagination());

        // Mensajes 8 y 9 del diagrama A.8: DeckRepository.findByPlayer, id mayor al cursor, límite más uno.
        List<StoredDeck> rows = decks.findByPlayer(command.playerId(), page.afterId(), page.fetchSize());

        // Mensajes 10 y 11 del diagrama A.8: Pagination.toPage recorta la fila extra y arma nextCursor.
        // Mensaje 12 del diagrama A.8: la página de DeckSummary vuelve a la API.
        return pagination.toPage(rows, page, StoredDeck::id).map(DeckSummary::of);
    }
}
