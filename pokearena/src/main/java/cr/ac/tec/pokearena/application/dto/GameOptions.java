package cr.ac.tec.pokearena.application.dto;

/**
 * Propósito: opciones del juego que la interfaz necesita para no escribir números a mano (sección 7.4 del
 * Sprint 3; decisión D-S3-11).
 * Responsabilidades: reunir los valores de las reglas del juego, tomados de las mismas clases que validan.
 * Referencia: sección 7.4; {@code GET /api/v1/opciones/juego}.
 *
 * <p>Crear Deck aporta los dos primeros campos. La historia de búsquedas agrega {@code minCubesToSearch} y
 * {@code searchPollSeconds} en su propio commit, al final (sección 1.3).
 *
 * @param deckSize          cantidad exacta de cartas de un deck ({@code DeckRules.DECK_SIZE})
 * @param deckNameMaxLength largo máximo del nombre del deck ({@code DeckRules.NAME_MAX_LENGTH})
 */
public record GameOptions(int deckSize, int deckNameMaxLength) {
}
