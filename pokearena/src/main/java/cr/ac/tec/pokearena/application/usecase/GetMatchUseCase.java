package cr.ac.tec.pokearena.application.usecase;

import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.GetMatchCommand;
import cr.ac.tec.pokearena.application.dto.MatchView;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.domain.error.MatchException;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;

/**
 * Propósito: consultar la vista inicial de una partida para uno de sus participantes.
 * Responsabilidades: ocultar ids inexistentes y ajenos, obtener la hora del servidor y construir la vista segura.
 * Referencia: sección 7.3; Anexo A.15, mensajes 5 a 12.
 */
public final class GetMatchUseCase {

    private final MatchRepository matches;
    private final Clock clock;

    public GetMatchUseCase(MatchRepository matches, Clock clock) {
        this.matches = matches;
        this.clock = clock;
    }

    /** Devuelve la vista mínima de la partida; un id inválido, inexistente o ajeno es MATCH_NOT_FOUND. */
    public MatchView execute(GetMatchCommand command) {
        // A.15, mensajes 5 a 7: el repositorio aplica el filtro de participación; ids inválidos también son 404.
        Optional<UUID> matchId = parseId(command.matchId());
        Match match = matchId.flatMap(id -> matches.findByIdForPlayer(id, command.playerId()))
                .orElseThrow(MatchException::notFound);
        // A.15, mensajes 8 a 12: la hora y la vista segura se calculan solo después de autorizar al participante.
        return MatchView.forSeat(match, command.playerId(), clock.now());
    }

    private static Optional<UUID> parseId(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
