package cr.ac.tec.pokearena.api.dto;

import cr.ac.tec.pokearena.application.dto.GameOptions;

/**
 * Propósito: cuerpo HTTP de {@code GET /api/v1/opciones/juego} (esquema {@code GameOptionsResponse}).
 * Responsabilidades: definir la forma JSON del contrato, separada de las opciones de la aplicación.
 * Referencia: sección 7.4 de la especificación del Sprint 3.
 *
 * @param deckSize          cantidad exacta de cartas de un deck
 * @param deckNameMaxLength largo máximo del nombre del deck
 */
public record GameOptionsResponse(int deckSize, int deckNameMaxLength) {

    public static GameOptionsResponse from(GameOptions options) {
        return new GameOptionsResponse(options.deckSize(), options.deckNameMaxLength());
    }
}
