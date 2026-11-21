package cr.ac.tec.pokearena.api.dto;

import java.time.Instant;
import java.util.List;

import cr.ac.tec.pokearena.api.dto.CardResponse.CatalogValue;
import cr.ac.tec.pokearena.application.dto.DeckDetails;

/**
 * Propósito: cuerpo HTTP de un deck con sus cartas (esquema {@code DeckResponse}).
 * Responsabilidades: definir la forma JSON del contrato, separada del resultado de la aplicación. El tipo y
 * la rareza usan la misma forma {@code { code, name }} que la ficha de una carta.
 * Referencia: sección 7.1; respuesta de {@code POST /api/v1/decks} y {@code GET /api/v1/decks/{id}}.
 *
 * @param id        identificador del deck
 * @param name      nombre
 * @param createdAt hora de creación
 * @param cards     sus cartas, ordenadas por código
 */
public record DeckResponse(long id, String name, Instant createdAt, List<DeckCardResponse> cards) {

    /** Carta de un deck (esquema {@code DeckCard}). */
    public record DeckCardResponse(String code, String name, CatalogValue type, CatalogValue rarity, int cost,
            int power, String imageSmallUrl) {
    }

    public static DeckResponse from(DeckDetails deck) {
        return new DeckResponse(deck.id(), deck.name(), deck.createdAt(), deck.cards().stream()
                .map(card -> new DeckCardResponse(card.code(), card.name(),
                        new CatalogValue(card.typeCode(), card.typeName()),
                        new CatalogValue(card.rarityCode(), card.rarityName()),
                        card.cost(), card.power(), card.imageSmallUrl()))
                .toList());
    }
}
