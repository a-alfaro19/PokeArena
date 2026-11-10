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
 * Propósito: API del catálogo del jugador (historia 3.1.2; sección 5.1).
 * Responsabilidades: traducir {@code GET /api/v1/catalogo} a un comando de Explorar el catálogo, con el
 * jugador tomado EXCLUSIVAMENTE de la sesión verificada por el interceptor, y su resultado a la respuesta
 * 200 con la página. No contiene reglas: filtros y paginación los valida el caso de uso.
 * Referencia: sección 8.3 y Anexo A.6 (mensajes 2 a 5, 17 y 18).
 *
 * <p>Decisiones:
 * <ul>
 *   <li>{@code @RequiresRole({PLAYER, ADMIN})} en la clase: la lista es explícita y sin jerarquía
 *       (sección 5.2); un administrador también es jugador. 401 sin sesión, 403 sin uno de esos roles.</li>
 *   <li>Los parámetros llegan en un {@code MultiValueMap}, como en Buscar carta: así un filtro
 *       desconocido, repetido o vacío lo informa el dominio con su error tipado, su {@code field} y sus
 *       {@code allowedValues}, en lugar de un error genérico del enlace de parámetros de Spring.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/catalogo")
@RequiresRole({Role.PLAYER, Role.ADMIN})
public class CatalogController {

    private final ExploreCatalogUseCase explore;

    public CatalogController(ExploreCatalogUseCase explore) {
        this.explore = explore;
    }

    /**
     * Explora el catálogo.
     *
     * <p>Orden: el interceptor ya autorizó (mensajes 3 y 4); el caso de uso valida y consulta (mensaje 5).
     */
    @GetMapping
    public PageResponse<CatalogCardResponse> explore(AuthenticatedUser user,
            @RequestParam MultiValueMap<String, String> parameters) {
        // Mensaje 5: explorarCatálogo(jugador, filtros, cursor, límite). Mensajes 17 y 18: 200 con las cartas.
        return PageResponse.from(explore.execute(new ExploreCatalogCommand(user.playerId(), parameters)),
                CatalogCardResponse::from);
    }
}
