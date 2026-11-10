package cr.ac.tec.pokearena.application.dto;

import java.util.Objects;

import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;

/**
 * Propósito: resultado de las credenciales de prueba ({@code dev/sesiones}, solo perfil dev; sección 5.2).
 * Responsabilidades: transportar a la API la sesión y el estado de la colección inicial del jugador de
 * prueba, con el mismo significado que en el inicio de sesión (sección 8.1, paso 8).
 * Referencia: secciones 5.2 y 8.1 (paso 6).
 *
 * @param session           jugador y credenciales propias
 * @param initialCollection estado de la colección inicial después de esta sesión; {@code null} para el
 *                          administrador de prueba, que no recibe colección
 */
public record DevSessionResult(Session session, InitialCollectionStatus initialCollection) {

    public DevSessionResult {
        Objects.requireNonNull(session, "La sesión es obligatoria");
    }
}
