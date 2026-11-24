package cr.ac.tec.pokearena.api.controller;

import java.util.Locale;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.MatchSearchResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.dto.AcceptBotMatchCommand;
import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.GetMatchSearchCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.AcceptBotMatchUseCase;
import cr.ac.tec.pokearena.application.usecase.CancelMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.GetMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.domain.model.Role;

/** Traduce la consulta, salida y aceptación del bot para el jugador autenticado (A.11 a A.13). */
@RestController
@RequestMapping("/api/v1/busquedas")
public class MatchSearchController {

    private final GetMatchSearchUseCase getSearch;
    private final CancelMatchSearchUseCase cancelSearch;
    private final AcceptBotMatchUseCase acceptBot;

    public MatchSearchController(GetMatchSearchUseCase getSearch, CancelMatchSearchUseCase cancelSearch,
            AcceptBotMatchUseCase acceptBot) {
        this.getSearch = getSearch;
        this.cancelSearch = cancelSearch;
        this.acceptBot = acceptBot;
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

    @PostMapping("/{id}/bot")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public ResponseEntity<MatchSearchResponse> acceptBot(AuthenticatedUser user, IdempotencyKeyHeader key,
            @PathVariable(name = "id") String id) {
        // A.13, mensaje 5. La operación usa una ruta plantilla; la huella distingue las búsquedas.
        String hash = RequestHash.builder().field("searchId", id.toLowerCase(Locale.ROOT)).build();
        var request = key.toRequest(user.playerId(), hash, HttpStatus.CREATED.value());
        var result = acceptBot.execute(request, new AcceptBotMatchCommand(id));
        return ResponseEntity.status(result.status()).body(MatchSearchResponse.from(result.body()));
    }
}
