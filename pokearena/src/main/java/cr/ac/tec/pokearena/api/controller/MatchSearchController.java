package cr.ac.tec.pokearena.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.MatchSearchResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.GetMatchSearchCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.CancelMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.GetMatchSearchUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/** Traduce la consulta y la salida de la búsqueda del jugador autenticado (A.11 y A.12). */
@RestController
@RequestMapping("/api/v1/busquedas")
public class MatchSearchController {

    private final GetMatchSearchUseCase getSearch;
    private final CancelMatchSearchUseCase cancelSearch;

    public MatchSearchController(GetMatchSearchUseCase getSearch, CancelMatchSearchUseCase cancelSearch) {
        this.getSearch = getSearch;
        this.cancelSearch = cancelSearch;
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public ResponseEntity<MatchSearchResponse> get(AuthenticatedUser user, @PathVariable(name = "id") String id) {
        // Mensajes 5 y 13 de A.11: la identidad viene de la sesión y cada consulta registra actividad.
        var details = getSearch.execute(new GetMatchSearchCommand(user.playerId(), id));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MatchSearchResponse.from(details));
    }

    @DeleteMapping("/{id}")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public MatchSearchResponse cancel(AuthenticatedUser user, @PathVariable(name = "id") String id) {
        // A.12, mensajes 5, 18 y 26: salir o devolver el estado de una búsqueda ya cerrada.
        return MatchSearchResponse.from(cancelSearch.execute(new CancelMatchSearchCommand(user.playerId(), id)));
    }
}
