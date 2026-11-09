package cr.ac.tec.pokearena.api.controller;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.CatalogCardResponse;
import cr.ac.tec.pokearena.api.dto.PageResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.application.dto.ExploreCatalogCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.ExploreCatalogUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: GET /api/v1/catalogo (US 43, sección 8.3 y mensajes 2 a 5 de A.6).
 * Responsabilidades: tomar la identidad exclusivamente del usuario verificado por el interceptor
 * y traducir la página al contrato HTTP. PLAYER y ADMIN tienen acceso explícito, sin jerarquía.
 */
@RestController
@RequestMapping("/api/v1/catalogo")
@RequiresRole({Role.PLAYER, Role.ADMIN})
public class CatalogController {

    private final ExploreCatalogUseCase explore;

    public CatalogController(ExploreCatalogUseCase explore) {
        this.explore = explore;
    }

    /** Conserva las repeticiones para que filtros y paginación inválidos tengan errores tipados. */
    @GetMapping
    public PageResponse<CatalogCardResponse> explore(AuthenticatedUser user,
            @RequestParam MultiValueMap<String, String> parameters) {
        return PageResponse.from(explore.execute(new ExploreCatalogCommand(user.playerId(), parameters)),
                CatalogCardResponse::from);
    }
}
