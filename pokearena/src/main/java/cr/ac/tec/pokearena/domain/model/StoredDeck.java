package cr.ac.tec.pokearena.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Propósito: deck guardado, sin el detalle de sus cartas (historia 3.1.3).
 * Responsabilidades: llevar el {@code id}, que da el orden estable de la paginación por cursor de
 * "Mis decks" (sección 5.5), y los datos del resumen: nombre, cantidad de cartas y fecha de creación.
 * Referencia: sección 7.1 ("Listar y consultar"); Anexo A.8, mensajes 9 y 10 ({@code findByPlayer}).
 *
 * @param id        identificador del deck (bigserial)
 * @param playerId  jugador dueño
 * @param name      nombre tal como lo escribió el jugador (sin espacios al inicio ni al final)
 * @param size      cantidad de cartas del deck
 * @param createdAt hora de creación
 */
public record StoredDeck(long id, UUID playerId, String name, int size, Instant createdAt) {

    public StoredDeck {
        Objects.requireNonNull(playerId, "El jugador es obligatorio");
        Objects.requireNonNull(name, "El nombre es obligatorio");
        Objects.requireNonNull(createdAt, "La hora de creación es obligatoria");
    }
}
