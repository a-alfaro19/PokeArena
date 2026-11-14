package cr.ac.tec.pokearena.api.dto;

import java.util.UUID;

import cr.ac.tec.pokearena.application.dto.DevSessionResult;
import cr.ac.tec.pokearena.application.dto.Session;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: respuesta HTTP de una sesión abierta: las credenciales de prueba (sección 5.2) y la base de
 * la respuesta del inicio de sesión (sección 8.1, paso 8).
 * Responsabilidades: entregar las credenciales propias, los datos básicos del jugador y el estado de su
 * colección inicial. El inicio de sesión con proveedor ({@link LoginResponse}) reutiliza esta forma y
 * agrega {@code firstLogin}.
 * Referencia: secciones 5.2 y 8.1.
 *
 * @param accessToken       token para {@code Authorization: Bearer}
 * @param tokenType         siempre {@code Bearer}
 * @param expiresIn         segundos de validez del token
 * @param player            jugador dueño de la sesión
 * @param initialCollection estado de la colección inicial; {@code null} para el administrador de prueba
 */
public record SessionResponse(String accessToken, String tokenType, long expiresIn, PlayerResponse player,
        InitialCollectionResponse initialCollection) {

    private static final String BEARER = "Bearer";

    /**
     * Datos públicos del jugador.
     *
     * @param displayName nombre del proveedor o, si no lo envió, el nombre de respaldo
     * @param email       correo informado por el proveedor, o {@code null}
     */
    public record PlayerResponse(UUID id, String displayName, String email, String role) {
    }

    /** Respuesta de {@code POST /api/v1/dev/sesiones}. */
    public static SessionResponse from(DevSessionResult result) {
        return from(result.session(), result.initialCollection());
    }

    static SessionResponse from(Session session, InitialCollectionStatus initialCollection) {
        return new SessionResponse(session.token().value(), BEARER, session.token().validity().toSeconds(),
                new PlayerResponse(session.player().id(), session.player().visibleName(), session.player().email(),
                        session.player().role().name()),
                InitialCollectionResponse.from(initialCollection));
    }
}
