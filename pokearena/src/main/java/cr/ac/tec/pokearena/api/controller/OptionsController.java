package cr.ac.tec.pokearena.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.CatalogOptionsResponse;
import cr.ac.tec.pokearena.api.dto.CreationOptionsResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.application.usecase.GetCatalogOptionsUseCase;
import cr.ac.tec.pokearena.application.usecase.GetCreationOptionsUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: API de las opciones que la interfaz web pide antes de mostrar filtros y formularios.
 * Responsabilidades: traducir {@code GET /api/v1/opciones/catalogo} y {@code GET /api/v1/opciones/creacion}
 * a sus casos de uso y su resultado a la respuesta 200. No contiene reglas ni valores propios.
 * Referencia: Anexos A.1, A.2, A.3 y A.6, mensajes 2 a 5 y 9 (la interfaz pide las opciones antes del POST
 * o del filtro; la seguridad valida la sesión y el rol; la API responde 200).
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Cada operación declara su rol en el método (sección 5.2): las opciones del catálogo las usan el
 *       jugador y el administrador ({@code {PLAYER, ADMIN}}, sin jerarquía); las de creación, solo el
 *       administrador. 401 sin sesión, 403 sin el rol.</li>
 *   <li>Un controlador para las dos: comparten propósito (alimentar la interfaz) y no tienen reglas.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/opciones")
public class OptionsController {

    private final GetCatalogOptionsUseCase catalogOptions;
    private final GetCreationOptionsUseCase creationOptions;

    public OptionsController(GetCatalogOptionsUseCase catalogOptions, GetCreationOptionsUseCase creationOptions) {
        this.catalogOptions = catalogOptions;
        this.creationOptions = creationOptions;
    }

    /** Tipos, rarezas, rango de costo y límites de paginación. */
    @GetMapping("/catalogo")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public CatalogOptionsResponse catalog() {
        return CatalogOptionsResponse.from(catalogOptions.execute());
    }

    /** Habilidades, rangos, energía extra, efectos y requisitos de imagen. */
    @GetMapping("/creacion")
    @RequiresRole(Role.ADMIN)
    public CreationOptionsResponse creation() {
        return CreationOptionsResponse.from(creationOptions.execute());
    }
}
