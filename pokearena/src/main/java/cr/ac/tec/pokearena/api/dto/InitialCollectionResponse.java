package cr.ac.tec.pokearena.api.dto;

import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: estado de la colección inicial en las respuestas de sesión (sección 8.1, paso 8).
 * Responsabilidades: entregar el estado y su mensaje. El mensaje va dentro del objeto porque pertenece
 * al estado. Lo comparten el inicio de sesión ({@link LoginResponse}) y las credenciales de prueba
 * ({@link SessionResponse}), así la interfaz lo muestra igual en los dos casos.
 * Referencia: sección 8.1, paso 8, y sección 5.2 (credenciales de prueba).
 *
 * @param status  {@code DELIVERED}, {@code ALREADY_DELIVERED}, {@code PENDING_NO_CARDS} o {@code PENDING_ERROR}
 * @param message texto para el jugador si la colección está pendiente; {@code null} si no lo está
 */
public record InitialCollectionResponse(String status, String message) {

    /** {@code null} si no hay estado (el administrador de prueba no recibe colección). */
    public static InitialCollectionResponse from(InitialCollectionStatus status) {
        return status == null ? null : new InitialCollectionResponse(status.name(), status.message());
    }
}
