package cr.ac.tec.pokearena.api.dto;

import cr.ac.tec.pokearena.api.dto.SessionResponse.PlayerResponse;
import cr.ac.tec.pokearena.application.dto.LoginResult;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: respuesta HTTP del inicio de sesión con proveedor (sección 8.1, paso 8).
 * Responsabilidades: entregar las credenciales propias, los datos del jugador, si fue su primer ingreso y
 * el estado de su colección inicial junto con su mensaje.
 * Referencia: sección 8.1, paso 8, y diagrama A.4 (mensaje 19).
 *
 * <p>Los campos de la sesión tienen la misma forma que {@link SessionResponse} (credenciales de prueba),
 * así la interfaz las trata igual.
 *
 * @param accessToken       token para {@code Authorization: Bearer}
 * @param tokenType         siempre {@code Bearer}
 * @param expiresIn         segundos de validez del token
 * @param player            jugador dueño de la sesión
 * @param firstLogin        {@code true} solo en el ingreso que creó la cuenta
 * @param initialCollection estado de la colección inicial y su mensaje
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, PlayerResponse player,
        boolean firstLogin, InitialCollectionResponse initialCollection) {

    /**
     * Estado de la colección inicial. El mensaje va dentro del objeto porque pertenece al estado.
     *
     * @param status  {@code DELIVERED}, {@code ALREADY_DELIVERED}, {@code PENDING_NO_CARDS} o {@code PENDING_ERROR}
     * @param message texto para el jugador si la colección está pendiente; {@code null} si no lo está
     */
    public record InitialCollectionResponse(String status, String message) {

        static InitialCollectionResponse from(InitialCollectionStatus status) {
            return new InitialCollectionResponse(status.name(), status.message());
        }
    }

    public static LoginResponse from(LoginResult result) {
        SessionResponse session = SessionResponse.from(result.session());
        return new LoginResponse(session.accessToken(), session.tokenType(), session.expiresIn(), session.player(),
                result.firstLogin(), InitialCollectionResponse.from(result.initialCollection()));
    }
}
