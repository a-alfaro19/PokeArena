package cr.ac.tec.pokearena.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.MatchResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.application.dto.GetMatchCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.GetMatchUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: API para consultar el inicio de una partida.
 * Responsabilidades: tomar el jugador exclusivamente de la sesión y traducir el resultado seguro del caso de uso
 * al contrato HTTP.
 * Referencia: sección 7.3; Anexo A.15, mensajes 2 a 4 y 12 a 17.
 */
@RestController
@RequestMapping("/api/v1/partidas")
public class MatchController {

    private final GetMatchUseCase getMatch;

    public MatchController(GetMatchUseCase getMatch) {
        this.getMatch = getMatch;
    }

    /** Consulta el inicio de una partida del jugador autenticado. */
    @GetMapping("/{id}")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public MatchResponse get(AuthenticatedUser user, @PathVariable(name = "id") String id) {
        // Mensajes 2 a 4 del diagrama A.15: GET, autorización previa y jugador de la sesión.
        // Mensajes 5 y 12 a 17: ejecuta el caso de uso y responde 200 o MATCH_NOT_FOUND.
        return MatchResponse.from(getMatch.execute(new GetMatchCommand(user.playerId(), id)));
    }
}
