package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: ubicación asignada a un espacio visible de una partida.
 * Responsabilidades: conservar el espacio sorteado y la ubicación referenciada por el catálogo.
 * Referencia: sección 6.2 y secciones 7.3, pasos 3 y 6.
 *
 * @param slot espacio de la ubicación en la partida
 * @param id identificador de la ubicación
 * @param location datos de la ubicación
 */
public record MatchLocation(int slot, long id, Location location) {

    public static final int FIRST_SLOT = 1;

    public MatchLocation {
        if (slot <= 0 || id <= 0) {
            throw new IllegalArgumentException("El espacio y el id de la ubicación deben ser positivos.");
        }
        Objects.requireNonNull(location, "La ubicación es obligatoria");
    }

    public MatchLocation(int slot, AvailableLocation available) {
        this(slot, available.id(), available.location());
    }
}
