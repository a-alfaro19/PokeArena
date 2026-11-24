package cr.ac.tec.pokearena.application.dto;

import cr.ac.tec.pokearena.domain.model.MatchContext;

/** Datos de ambos asientos que necesita la preparación de una partida (A.14). */
public record MatchRequest(MatchContext context, MatchSeat first, MatchSeat second) {
}
