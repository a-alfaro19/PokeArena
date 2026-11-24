package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: estado persistido de una partida.
 * Responsabilidades: distinguir partidas activas de las ya finalizadas.
 * Referencia: sección 6.2 y esquema MatchResponse.
 */
public enum MatchStatus {
    ACTIVE,
    FINISHED
}
