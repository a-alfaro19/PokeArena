package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: ubicación asignada a un espacio de la partida.
 * Responsabilidades: conservar su id y configuración, aunque después deje de estar disponible para partidas nuevas.
 * Referencia: sección 6.2; sección 7.3, paso 3; Anexo A.14.
 */
public record MatchLocation(int slot, long locationId, Location location) {

    public static final int FIRST_SLOT = 1;
    public static final int LAST_SLOT = 3;

    public MatchLocation(int slot, AvailableLocation location) {
        this(slot, requireAvailableLocation(location).id(), location.location());
    }

    public MatchLocation {
        Objects.requireNonNull(location, "La ubicación de partida es obligatoria");
        if (slot < FIRST_SLOT || slot > LAST_SLOT || locationId <= 0) {
            throw new IllegalArgumentException("El espacio de ubicación debe estar entre 1 y 3");
        }
    }

    private static AvailableLocation requireAvailableLocation(AvailableLocation location) {
        return Objects.requireNonNull(location, "La ubicación de partida es obligatoria");
    }
}
