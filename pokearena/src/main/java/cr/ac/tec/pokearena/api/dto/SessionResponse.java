package cr.ac.tec.pokearena.api.dto;

import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.Session;

/**
 * Propósito: respuesta HTTP de una sesión abierta (sección 5.2; base de la respuesta de 8.1, paso 8).
 * Responsabilidades: entregar las credenciales propias y los datos básicos del jugador. El
 * inicio de sesión con proveedor ({@link LoginResponse}) reutiliza esta forma y agrega
 * {@code firstLogin} e {@code initialCollection}.
 * Referencia: secciones 5.2 y 8.1.
 *
 * @param accessToken token para {@code Authorization: Bearer}
 * @param tokenType   siempre {@code Bearer}
 * @param expiresIn   segundos de validez del token
 * @param player      jugador dueño de la sesión
 */
public record SessionResponse(String accessToken, String tokenType, long expiresIn, PlayerResponse player) {

    private static final String BEARER = "Bearer";

    /**
     * Datos públicos del jugador.
     *
     * @param displayName nombre del proveedor o, si no lo envió, el nombre de respaldo
     * @param email       correo informado por el proveedor, o {@code null}
     */
    public record PlayerResponse(UUID id, String displayName, String email, String role) {
    }

    public static SessionResponse from(Session session) {
        return new SessionResponse(session.token().value(), BEARER, session.token().validity().toSeconds(),
                new PlayerResponse(session.player().id(), session.player().visibleName(), session.player().email(),
                        session.player().role().name()));
    }
}
