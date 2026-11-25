package cr.ac.tec.pokearena.api.controller;

import java.util.Locale;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.MatchSearchResponse;
import cr.ac.tec.pokearena.api.dto.StartMatchSearchRequest;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.dto.AcceptBotMatchCommand;
import cr.ac.tec.pokearena.application.dto.CancelMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.GetMatchSearchCommand;
import cr.ac.tec.pokearena.application.dto.StartMatchSearchCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.AcceptBotMatchUseCase;
import cr.ac.tec.pokearena.application.usecase.CancelMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.GetMatchSearchUseCase;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.application.usecase.StartMatchSearchUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/** Expone el alta, la consulta, la salida y la aceptación del bot (A.9 y A.11 a A.13). */
@RestController
@RequestMapping("/api/v1/busquedas")
public class MatchSearchController {

    private final StartMatchSearchUseCase startSearch;
    private final GetMatchSearchUseCase getSearch;
    private final CancelMatchSearchUseCase cancelSearch;
    private final AcceptBotMatchUseCase acceptBot;

    public MatchSearchController(StartMatchSearchUseCase startSearch, GetMatchSearchUseCase getSearch,
            CancelMatchSearchUseCase cancelSearch, AcceptBotMatchUseCase acceptBot) {
        this.startSearch = startSearch;
        this.getSearch = getSearch;
        this.cancelSearch = cancelSearch;
        this.acceptBot = acceptBot;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public ResponseEntity<MatchSearchResponse> start(AuthenticatedUser user, IdempotencyKeyHeader key,
            @RequestBody(required = false) StartMatchSearchRequest body) {
        // A.9: la huella distingue un cambio de deck o contexto bajo la misma clave.
        Long deckId = body == null ? null : body.deckId();
        String context = body == null ? null : body.context();
        String hash = RequestHash.builder().field("deckId", deckId).field("context", context).build();
        var request = key.toRequest(user.playerId(), hash, HttpStatus.CREATED.value());
        var result = startSearch.execute(request, new StartMatchSearchCommand(deckId, context));
        return ResponseEntity.status(result.status()).body(MatchSearchResponse.from(result.body()));
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
