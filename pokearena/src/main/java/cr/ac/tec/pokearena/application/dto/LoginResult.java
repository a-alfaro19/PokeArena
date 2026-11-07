package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;

import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: resultado del inicio de sesión con proveedor (sección 8.1, paso 8).
 * Responsabilidades: transportar a la API la sesión (jugador y credenciales propias), si fue el primer
 * ingreso y el estado de la colección inicial.
 * Referencia: sección 8.1, paso 8, y diagrama A.4 (mensaje 18).
 *
 * @param session           jugador y credenciales propias
 * @param firstLogin        {@code true} solo en el ingreso que creó la cuenta
 * @param initialCollection estado de la colección inicial después de este ingreso
 */
public record LoginResult(Session session, boolean firstLogin, InitialCollectionStatus initialCollection) {

    public LoginResult {
        Objects.requireNonNull(session, "La sesión es obligatoria");
        Objects.requireNonNull(initialCollection, "El estado de la colección inicial es obligatorio");
    }
}
