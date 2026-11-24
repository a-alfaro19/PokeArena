package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: zona de una carta dentro de una partida (sección 6.2).
 * Responsabilidades: distinguir cartas en el deck, en la mano o en el cementerio para su persistencia y
 * representación.
 * Referencia: secciones 6.2 y 7.3; criterios C.10-E2, E3 y E8.
 */
public enum MatchZone {
    DECK,
    HAND,
    GRAVEYARD
}
