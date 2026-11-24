package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: ubicación activa y disponible con el identificador que se persiste en una partida.
 * Responsabilidades: mantener juntos el id de la fila y el modelo validado que necesitan las reglas de
 * selección y el inicio de turno.
 * Referencia: sección 7.3, pasos 2 y 3; Anexo A.14.
 *
 * @param id       identificador de la ubicación
 * @param location ubicación del catálogo
 */
public record AvailableLocation(long id, Location location) {

    public AvailableLocation {
        if (id <= 0) {
            throw new IllegalArgumentException("El id de la ubicación debe ser positivo.");
        }
        Objects.requireNonNull(location, "La ubicación es obligatoria");
        if (!location.available()) {
            throw new IllegalArgumentException("La ubicación debe estar disponible para partidas nuevas.");
        }
    }
}
