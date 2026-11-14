package cr.ac.tec.pokearena.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.PublicConfigurationResponse;
import cr.ac.tec.pokearena.api.security.PublicRoute;

/**
 * Propósito: configuración pública que necesita la interfaz web al cargar (sección 11, "con el client id
 * leído de un endpoint de configuración").
 * Responsabilidades: responder {@code GET /api/v1/configuracion} con el {@code client id} de Google, o
 * {@code null} si el servidor no lo tiene configurado. Nada más.
 * Referencia: sección 11 (pantalla de ingreso) y sección 8.1 (audiencia del token de Google).
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Es pública ({@code @PublicRoute}): la interfaz la pide antes de que exista una sesión, justamente
 *       para poder ofrecer el botón "Entrar con Google".</li>
 *   <li>El {@code client id} no es un secreto: Google lo publica en cada página que muestra su botón. Aun
 *       así, este endpoint expone solo ese valor, para no filtrar por descuido otra configuración.</li>
 *   <li>Lee la misma propiedad que verifica la audiencia ({@code app.identity.google.client-id}), así la
 *       interfaz y el verificador nunca usan valores distintos. No es una regla de negocio, por eso vive
 *       en la API y no en un caso de uso.</li>
 * </ul>
 */
@RestController
public class ConfigurationController {

    private final PublicConfigurationResponse configuration;

    public ConfigurationController(@Value("${app.identity.google.client-id:}") String googleClientId) {
        this.configuration = PublicConfigurationResponse.of(googleClientId);
    }

    @PublicRoute
    @GetMapping("/api/v1/configuracion")
    public PublicConfigurationResponse get() {
        return configuration;
    }
}
