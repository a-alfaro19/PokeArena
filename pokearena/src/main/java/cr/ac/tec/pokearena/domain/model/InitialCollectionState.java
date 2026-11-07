package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: estado guardado de la colección inicial de un jugador (sección 6, columna
 * {@code player.initial_collection_status}).
 * Responsabilidades: decir si el jugador ya recibió su colección inicial. Es lo que se guarda en la
 * base; el resultado de un intento de entrega (por ejemplo "pendiente por falta de cartas") es otra
 * cosa y lo informa la aplicación.
 * Referencia: sección 6 y sección 8.2 (pasos 2 y 7).
 */
public enum InitialCollectionState {
    /** Todavía no la recibió: el siguiente ingreso intenta entregarla. */
    PENDING,
    /** Ya la recibió: nunca se entrega otra vez. */
    DELIVERED
}
