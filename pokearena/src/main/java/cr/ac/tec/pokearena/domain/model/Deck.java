package cr.ac.tec.pokearena.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Propósito: deck guardado con el detalle de sus cartas (historia 3.1.3).
 * Responsabilidades: unir el deck guardado con sus cartas, leídas en una sola consulta, y ofrecer sus
 * códigos para quien solo los necesita (por ejemplo, la historia de búsquedas para validar el deck).
 * Referencia: sección 7.1 ({@code GET /api/v1/decks/{id}}: "las cartas obtenidas en una consulta");
 * Anexo A.8, mensaje 19 ({@code findByIdAndPlayer}: "deck con sus 12 cartas").
 *
 * @param stored datos del deck guardado
 * @param cards  sus cartas, ordenadas por código
 */
public record Deck(StoredDeck stored, List<Card> cards) {

    public Deck {
        Objects.requireNonNull(stored, "El deck es obligatorio");
        cards = List.copyOf(cards);
        if (cards.size() != stored.size()) {
            // Invariante: el resumen y el detalle hablan del mismo deck.
            throw new IllegalArgumentException("La cantidad de cartas no coincide con el tamaño del deck");
        }
    }

    /** Códigos de las cartas del deck, en el mismo orden que {@link #cards()}. */
    public List<String> cardCodes() {
        return cards.stream().map(card -> card.code().value()).toList();
    }
}
