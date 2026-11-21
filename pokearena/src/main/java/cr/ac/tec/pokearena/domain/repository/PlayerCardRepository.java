package cr.ac.tec.pokearena.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.OwnedCard;

/**
 * Propósito: puerto de persistencia de la colección de cartas de cada jugador (patrón Repository,
 * sección 4.4; tabla {@code player_card}).
 * Responsabilidades: agregar cartas a la colección de un jugador y comprobar cuáles posee (Crear Deck).
 * Referencia: sección 6 (tabla player_card) y sección 8.2, paso 5.
 *
 * <p>{@link #addOneCopyOfEach} debe llamarse dentro de un {@code UnitOfWork}: la entrega de la colección inicial guarda las cartas,
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

    /**
     * Las cartas de {@code cardCodes} que el jugador posee, con sus datos y sus copias, en UNA consulta por
     * la clave {@code (player_id, card_code)}: nunca una por carta ni un recorrido del catálogo (sección 7.1,
     * paso 4; Anexo A.7, mensaje 19). Los códigos que no posee, o que no existen, simplemente no aparecen.
     *
     * <p>No filtra por disponibilidad: poseer una carta no depende de que siga visible en el catálogo. Es una
     * lectura y no exige transacción (historia 3.1.3, Sprint 3).
     *
     * @param playerId  jugador de la sesión
     * @param cardCodes códigos a comprobar
     * @return las cartas poseídas, ordenadas por código; vacía si no posee ninguna
     */
    List<OwnedCard> findOwnedCards(UUID playerId, Collection<String> cardCodes);
}
