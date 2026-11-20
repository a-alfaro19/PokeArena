package cr.ac.tec.pokearena.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.model.NewDeck;
import cr.ac.tec.pokearena.domain.model.StoredDeck;

/**
 * Propósito: puerto de persistencia de los decks de los jugadores (patrón Repository; tablas {@code deck} y
 * {@code deck_card}).
 * Responsabilidades: guardar un deck validado con sus cartas, y leer los decks de un jugador: uno con sus
 * cartas, la lista paginada por cursor y los códigos de un deck.
 * Referencia: sección 7.1 y Anexo D.1 del Sprint 3; Anexo A.7 (mensaje 19) y A.8 (mensajes 8 y 19).
 *
 * <p>Un jugador solo ve sus propios decks: las lecturas reciben siempre el jugador de la sesión, y un deck
 * ajeno se trata igual que uno inexistente (vacío), sin revelar si existe (sección 5.2).
 */
public interface DeckRepository {

    /**
     * Guarda el deck y sus cartas. Debe llamarse dentro de un {@code UnitOfWork}: el deck, sus cartas y la
     * clave de idempotencia se confirman o se revierten juntos (Anexo A.7, mensajes 18 a 22).
     *
     * @param deck deck ya validado por {@code DeckRules}
     * @return el deck guardado, con su {@code id}
     * @throws cr.ac.tec.pokearena.domain.error.DeckException {@code DECK_NAME_TAKEN} si el jugador ya tiene
     *         un deck con el mismo nombre normalizado (también bajo concurrencia)
     */
    StoredDeck save(NewDeck deck);

    /**
     * El deck con sus cartas, leídas en una sola consulta, si existe y es del jugador.
     *
     * @return vacío si no existe o si pertenece a otro jugador (mismo resultado para ambos casos)
     */
    Optional<Deck> findByIdAndPlayer(long deckId, UUID playerId);

    /**
     * Una página de los decks del jugador, ordenados por {@code id} (paginación por cursor, sección 5.5).
     *
     * @param afterId último {@code id} de la página anterior (0 al principio)
     * @param maxRows filas como máximo: el tamaño de página más uno, para saber si hay otra página
     */
    List<StoredDeck> findByPlayer(UUID playerId, long afterId, int maxRows);

    /**
     * Códigos de las cartas de un deck, ordenados. Lo usa el emparejamiento de la historia 4.1.1, que ya
     * comprobó a qué jugador pertenece la búsqueda (sección 7.2.2).
     */
    List<String> findCardCodes(long deckId);
}
