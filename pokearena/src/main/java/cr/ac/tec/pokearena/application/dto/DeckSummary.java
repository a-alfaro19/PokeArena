package cr.ac.tec.pokearena.application.dto;

import java.time.Instant;

import cr.ac.tec.pokearena.domain.model.StoredDeck;

/**
 * Propósito: un deck en la lista de "Mis decks" (historia 3.1.3).
 * Responsabilidades: llevar los datos del resumen, sin las cartas.
 * Referencia: sección 7.1 ({@code DeckSummaryResponse}: id, name, size, createdAt); Anexo A.8, mensaje 12.
 *
 * @param id        identificador del deck
 * @param name      nombre
 * @param size      cantidad de cartas
 * @param createdAt hora de creación
 */
public record DeckSummary(long id, String name, int size, Instant createdAt) {

    public static DeckSummary of(StoredDeck deck) {
        return new DeckSummary(deck.id(), deck.name(), deck.size(), deck.createdAt());
    }
}
