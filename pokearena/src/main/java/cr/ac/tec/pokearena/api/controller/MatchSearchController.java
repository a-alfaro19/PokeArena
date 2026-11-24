package cr.ac.tec.pokearena.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.MatchSearchResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.application.dto.GetMatchSearchCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.GetMatchSearchUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/** Traduce la consulta HTTP a la búsqueda del jugador autenticado (A.11). */
@RestController
@RequestMapping("/api/v1/busquedas")
public class MatchSearchController {

    private final GetMatchSearchUseCase getSearch;

    public MatchSearchController(GetMatchSearchUseCase getSearch) {
        this.getSearch = getSearch;
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public ResponseEntity<MatchSearchResponse> get(AuthenticatedUser user, @PathVariable(name = "id") String id) {
        // Mensajes 5 y 13 de A.11: la identidad viene de la sesión y cada consulta registra actividad.
        var details = getSearch.execute(new GetMatchSearchCommand(user.playerId(), id));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MatchSearchResponse.from(details));
    }
}
