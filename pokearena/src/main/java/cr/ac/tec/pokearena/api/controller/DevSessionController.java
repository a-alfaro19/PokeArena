package cr.ac.tec.pokearena.api.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.DevSessionRequest;
import cr.ac.tec.pokearena.api.dto.SessionResponse;
import cr.ac.tec.pokearena.api.security.PublicRoute;
import cr.ac.tec.pokearena.application.usecase.IssueDevSessionUseCase;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: credenciales de prueba para ADMIN o PLAYER (sección 5.1, "Credenciales de prueba").
 * Responsabilidades: recibir el rol pedido y devolver credenciales propias de un jugador real con
 * proveedor dev. Es público: sirve justamente para obtener un token en desarrollo.
 * Referencia: sección 5.2 y RNF-SEG-10.
 *
 * <p>{@code @Profile("dev")}: con cualquier otro perfil (por ejemplo prod) Spring no crea este
 * controlador, así la ruta no existe y responde 404.
 */
@RestController
@Profile("dev")
public class DevSessionController {

    private final IssueDevSessionUseCase issueDevSession;

    public DevSessionController(IssueDevSessionUseCase issueDevSession) {
        this.issueDevSession = issueDevSession;
    }

    // Pública: sirve para obtener credenciales, así que no puede exigirlas.
    @PublicRoute
    @PostMapping(path = "/api/v1/dev/sesiones", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SessionResponse create(@RequestBody DevSessionRequest request) {
        Role role = Role.fromCode(DevSessionRequest.ROLE_FIELD, request.role());
        return SessionResponse.from(issueDevSession.execute(role));
    }
}
