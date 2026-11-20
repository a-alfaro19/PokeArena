package cr.ac.tec.pokearena.api.dto;

import java.time.Instant;

import cr.ac.tec.pokearena.application.dto.DeckSummary;

/**
 * Propósito: cuerpo HTTP de un deck en la lista de "Mis decks" (esquema {@code DeckSummaryResponse}).
 * Responsabilidades: definir la forma JSON del contrato.
 * Referencia: sección 7.1 ({@code id}, {@code name}, {@code size}, {@code createdAt}).
 *
 * @param id        identificador del deck
 * @param name      nombre
 * @param size      cantidad de cartas
 * @param createdAt hora de creación
 */
public record DeckSummaryResponse(long id, String name, int size, Instant createdAt) {

    public static DeckSummaryResponse from(DeckSummary deck) {
        return new DeckSummaryResponse(deck.id(), deck.name(), deck.size(), deck.createdAt());
    }
}
