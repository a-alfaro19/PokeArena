package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: carta leída de la base junto con su identificador interno.
 * Responsabilidades: llevar el {@code id}, que da el orden estable de la paginación por cursor
 * (sección 5.5), junto con la carta. El {@code id} no se muestra: el administrador ve el código.
 * Referencia: sección 5.5 ("orden por id ascendente y cursor por clave") y sección 7.1 ("el orden
 * siempre se toma del id, nunca del código").
 *
 * @param id   identificador de la fila (bigserial)
 * @param card la carta
 */
public record StoredCard(long id, Card card) {

    public StoredCard {
        Objects.requireNonNull(card, "La carta es obligatoria");
    }
}
