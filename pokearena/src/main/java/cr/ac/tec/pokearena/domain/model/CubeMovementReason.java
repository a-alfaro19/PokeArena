package cr.ac.tec.pokearena.domain.model;

/**
 * Propósito: motivo de un movimiento de cubos (sección 6, columna {@code cube_movement.reason}).
 * Responsabilidades: nombrar por qué cambió el saldo de un jugador. Las historias de compras y premios
 * agregarán sus motivos cuando se implementen.
 * Referencia: sección 6 y sección 8.2, paso 6.
 */
public enum CubeMovementReason {
    /** Cubos de la colección inicial: el primer movimiento del historial de cada jugador. */
    INITIAL_GRANT
}
