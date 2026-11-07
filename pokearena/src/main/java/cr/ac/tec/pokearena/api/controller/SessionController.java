package cr.ac.tec.pokearena.api.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.LoginRequest;
import cr.ac.tec.pokearena.api.dto.LoginResponse;
import cr.ac.tec.pokearena.api.security.PublicRoute;
import cr.ac.tec.pokearena.application.dto.LoginCommand;
import cr.ac.tec.pokearena.application.usecase.LoginWithProviderUseCase;

/**
 * Propósito: API del inicio de sesión con proveedor (historia 1.1.1; sección 5.1).
 * Responsabilidades: traducir {@code POST /api/v1/sesiones} a un comando del caso de uso y su resultado a
 * la respuesta 200. No contiene reglas: los campos, el proveedor y el token los valida el caso de uso.
 * Referencia: sección 8.1 (pasos 1 y 8) y Anexo A.4 (mensajes 4, 5, 18 y 19).
 *
 * <p>Es pública ({@code @PublicRoute}): quien llama todavía no tiene credenciales, justamente las pide.
 * Tampoco mira el encabezado {@code Authorization}: el token del proveedor viaja en el cuerpo y nunca se
 * usa para autorizar otras operaciones.
 */
@RestController
public class SessionController {

    private final LoginWithProviderUseCase login;

    public SessionController(LoginWithProviderUseCase login) {
        this.login = login;
    }

    @PublicRoute
    @PostMapping(path = "/api/v1/sesiones", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse create(@RequestBody LoginRequest request) {
        // Mensaje 5: iniciarSesión(proveedor, token). Mensajes 18 y 19: 200 con token, jugador y colección.
        return LoginResponse.from(login.execute(new LoginCommand(request.provider(), request.idToken())));
    }
}
