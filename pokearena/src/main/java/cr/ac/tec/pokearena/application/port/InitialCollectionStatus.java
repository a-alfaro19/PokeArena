package cr.ac.tec.pokearena.application.port;

/**
 * Propósito: resultado de la entrega de la colección inicial que informa el inicio de sesión
 * (sección 8.1, paso 8: campo {@code initialCollection}).
 * Responsabilidades: nombrar los cuatro resultados posibles y, cuando la colección queda pendiente,
 * el mensaje en español que ve el jugador.
 * Referencia: sección 8.1, paso 8, y criterios C.5 ("el jugador ve un mensaje que explica que el
 * juego aún no está disponible").
 */
public enum InitialCollectionStatus {

    /** Se entregó en este ingreso. */
    DELIVERED(null),

    /** Ya se había entregado en un ingreso anterior: no se entrega de nuevo. */
    ALREADY_DELIVERED(null),

    /** El catálogo todavía no tiene 15 cartas Comunes: la cuenta existe sin colección ni cubos. */
    PENDING_NO_CARDS("El juego aún no está disponible: todavía no hay cartas suficientes para tu colección "
            + "inicial. La recibirás en un próximo ingreso."),

    /** La entrega falló sin dejar nada a medias: se completará en el siguiente ingreso. */
    PENDING_ERROR("No pudimos entregar tu colección inicial en este momento. Se completará en tu próximo ingreso.");

    private final String message;

    InitialCollectionStatus(String message) {
        this.message = message;
    }

    /** Mensaje para el jugador si la colección quedó pendiente; {@code null} si no lo está. */
    public String message() {
        return message;
    }
}
