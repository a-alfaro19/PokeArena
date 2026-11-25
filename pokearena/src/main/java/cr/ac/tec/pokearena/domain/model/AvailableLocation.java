package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: ubicación del catálogo disponible para una partida nueva.
 * Responsabilidades: asociar el id persistente con la ubicación validada.
 * Referencia: sección 7.3, pasos 2 y 3; Anexo A.14.
 */
public record AvailableLocation(long id, Location location) {

    public AvailableLocation {
        Objects.requireNonNull(location, "La ubicación es obligatoria");
        if (id <= 0 || !location.available()) {
            throw new IllegalArgumentException("La ubicación debe tener id positivo y estar disponible");
        }
    }
}
