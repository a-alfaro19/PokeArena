package cr.ac.tec.pokearena.application.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.model.StoredDeck;

/**
 * Propósito: resultado de Crear deck y de Consultar un deck (historia 3.1.3).
 * Responsabilidades: llevar el deck con la ficha resumida de cada carta (código, nombre, tipo, rareza,
 * costo, poder y miniatura). Es también lo que se guarda como respuesta idempotente: por eso es un record
 * de valores simples, que se serializa y se vuelve a leer igual.
 * Referencia: sección 7.1 ({@code DeckResponse}); Anexo A.7, mensaje 34, y A.8, mensaje 22.
 *
 * @param id        identificador del deck
 * @param name      nombre tal como lo escribió el jugador
 * @param createdAt hora de creación
 * @param cards     sus cartas, ordenadas por código
 */
public record DeckDetails(long id, String name, Instant createdAt, List<DeckCard> cards) {

    public DeckDetails {
        cards = List.copyOf(cards);
    }

    /**
     * Una carta del deck.
     *
     * @param code          código de la carta
     * @param name          nombre
     * @param typeCode      código del tipo
     * @param typeName      nombre del tipo
     * @param rarityCode    código de la rareza
     * @param rarityName    nombre de la rareza
     * @param cost          costo
     * @param power         poder
     * @param imageSmallUrl miniatura
     */
    public record DeckCard(String code, String name, String typeCode, String typeName, String rarityCode,
            String rarityName, int cost, int power, String imageSmallUrl) {

        static DeckCard of(Card card) {
            return new DeckCard(card.code().value(), card.fields().name(), card.fields().type().code(),
                    card.fields().type().name(), card.fields().rarity().code(), card.fields().rarity().name(),
                    card.fields().cost(), card.fields().power(), card.imageSmallUrl());
        }
    }

    /** Detalle de un deck leído con sus cartas. */
    public static DeckDetails of(Deck deck) {
        return of(deck.stored(), deck.cards());
    }

    /** Detalle de un deck recién guardado, con las cartas ya leídas al comprobar su posesión. */
    public static DeckDetails of(StoredDeck stored, List<Card> cards) {
        return new DeckDetails(stored.id(), stored.name(), stored.createdAt(), cards.stream()
                .sorted(Comparator.comparing(card -> card.code().value()))
                .map(DeckCard::of)
                .toList());
    }
}
