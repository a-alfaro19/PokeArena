package cr.ac.tec.pokearena.domain.repository;

import cr.ac.tec.pokearena.domain.model.CubeMovement;

/**
 * Propósito: puerto de persistencia de los cubos de cada jugador (patrón Repository, sección 4.4; tabla
 * {@code cube_movement} y columna {@code player.cube_balance}).
 * Responsabilidades: registrar un movimiento y aplicarlo al saldo, siempre juntos, para que se cumpla el
 * invariante "el saldo es la suma de los movimientos" (sección 9).
 * Referencia: sección 6, sección 8.2 (paso 6) y sección 9 (invariantes de cubos).
 *
 * <p>Un solo método que hace las dos cosas: si fueran dos, un caso de uso podría olvidar una y romper el
 * invariante. Debe llamarse dentro de un {@code UnitOfWork}. El saldo nunca queda negativo: lo impide la
 * restricción {@code ck_player_cube_balance_non_negative} de la base.
 */
public interface CubeRepository {

    /** Registra el movimiento en el historial y suma su monto al saldo del jugador. */
    void apply(CubeMovement movement);
}
