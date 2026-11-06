package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Propósito: datos con los que se crea un jugador que todavía no existe (sección 8.1, paso 5).
 * Responsabilidades: llevar al repositorio lo que decidió la aplicación (rol y fecha de creación)
 * junto con lo que informó el proveedor (nombre y correo). El repositorio no decide nada: solo guarda.
 * Referencia: sección 8.1, paso 5.
 *
 * @param displayName nombre visible, puede ser nulo
 * @param email       correo, puede ser nulo
 * @param role        rol que tendrá la cuenta nueva
 * @param createdAt   momento de creación, tomado del puerto Clock
 */
public record NewPlayer(String displayName, String email, Role role, Instant createdAt) {

    public NewPlayer {
        Objects.requireNonNull(role, "El rol es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de creación es obligatoria");
    }
}
