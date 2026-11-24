package cr.ac.tec.pokearena.application.dto;

import java.util.UUID;

/** Partida preparada dentro de la transacción del solicitante. */
public record MatchStarted(UUID matchId) {
}
