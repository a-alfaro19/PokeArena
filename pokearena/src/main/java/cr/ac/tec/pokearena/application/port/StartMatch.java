package cr.ac.tec.pokearena.application.port;

import cr.ac.tec.pokearena.application.dto.MatchRequest;
import cr.ac.tec.pokearena.application.dto.MatchStarted;

/** Contrato para StartMatchUseCase (7.3): preparar la partida en la transacción activa. */
@FunctionalInterface
public interface StartMatch {
    MatchStarted execute(MatchRequest request);
}
