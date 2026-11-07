package cr.ac.tec.pokearena.domain.repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Propósito: puerto de persistencia de la colección de cartas de cada jugador (patrón Repository,
 * sección 4.4; tabla {@code player_card}).
 * Responsabilidades: agregar cartas a la colección de un jugador.
 * Referencia: sección 6 (tabla player_card) y sección 8.2, paso 5.
 *
 * <p>Debe llamarse dentro de un {@code UnitOfWork}: la entrega de la colección inicial guarda las cartas,
 * los cubos y la marca de entregada en una sola transacción ("todo o nada").
 */
public interface PlayerCardRepository {

    /**
     * Agrega una copia de cada carta a la colección del jugador. Si el jugador ya tenía alguna, suma una
     * copia más (una fila por carta, con su cantidad de copias).
     *
     * @param playerId  jugador dueño de la colección
     * @param cardCodes códigos de cartas existentes, sin repetidos
     */
    void addOneCopyOfEach(UUID playerId, Collection<String> cardCodes);
}
