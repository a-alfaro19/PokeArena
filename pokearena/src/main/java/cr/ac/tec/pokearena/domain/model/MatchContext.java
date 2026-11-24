package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: clasificar el origen de una partida.
 * Responsabilidades: distinguir partidas clasificatorias de partidas de torneo, manteniendo el contexto que
 * se persiste y se presenta.
 * Referencia: sección 6.2 y sección 7.3; criterios C.10-E7 y C.10-A3.
 */
public enum MatchContext {
    RANKED,
    TOURNAMENT
}
