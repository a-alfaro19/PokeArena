package cr.ac.tec.pokearena.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
 * Propósito: exponer la vista inicial y segura de una partida.
 * Responsabilidades: exigir sesión de jugador y no cachear estado privado del juego.
 * Referencia: sección 7.3; Anexo A.15, mensajes 1 a 13.
 */
@RestController
@RequestMapping("/api/v1/partidas")
public class MatchController {

    private final GetMatchUseCase getMatch;

    public MatchController(GetMatchUseCase getMatch) {
        this.getMatch = getMatch;
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public ResponseEntity<MatchResponse> get(AuthenticatedUser user, @PathVariable(name = "id") String id) {
        var view = getMatch.execute(new GetMatchCommand(user.playerId(), id));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MatchResponse.from(view));
    }
}
