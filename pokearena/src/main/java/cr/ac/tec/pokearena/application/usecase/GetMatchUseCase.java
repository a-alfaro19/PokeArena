package cr.ac.tec.pokearena.application.usecase;

import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.GetMatchCommand;
import cr.ac.tec.pokearena.application.dto.MatchStartView;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.domain.error.MatchException;
import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.MatchParticipant;
import cr.ac.tec.pokearena.domain.repository.MatchRepository;
import cr.ac.tec.pokearena.domain.rule.MatchView;

/**
 * Propósito: consultar el inicio de una partida del jugador autenticado.
 * Responsabilidades: ocultar ids mal formados, partidas inexistentes y partidas ajenas bajo MATCH_NOT_FOUND, y
 * construir una vista sin información privada de los decks ni de la mano rival.
 * Referencia: sección 7.3; Anexo A.15, mensajes 5 a 12; criterio C.10-A2.
 */
public class GetMatchUseCase {

    static final String NOT_FOUND_MESSAGE = "La partida no existe.";

    private final MatchRepository matches;
    private final Clock clock;
    private final MatchView view;

    public GetMatchUseCase(MatchRepository matches, Clock clock, MatchView view) {
        this.matches = matches;
        this.clock = clock;
        this.view = view;
    }

    public MatchStartView execute(GetMatchCommand command) {
        Optional<UUID> id = parseId(command.matchId());
        // Mensajes 6 y 7 del diagrama A.15: repositorio devuelve vacío también para ids de otro jugador.
        Match match = id.flatMap(value -> matches.findByIdForPlayer(value, command.playerId()))
                .orElseThrow(() -> MatchException.notFound(NOT_FOUND_MESSAGE));
        MatchParticipant own = match.participants().stream()
                .filter(participant -> command.playerId().equals(participant.playerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El repositorio devolvió una partida sin el jugador."));
        // Mensajes 8 y 9 del diagrama A.15: consulta la hora solo para una partida visible.
        var now = clock.now();
        // Mensajes 10 a 12 del diagrama A.15: proyecta por asiento y devuelve el resultado seguro a la API.
        return MatchStartView.from(view.forSeat(match, own.seat(), now));
    }

    private static Optional<UUID> parseId(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(text);
            return id.toString().equalsIgnoreCase(text) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
