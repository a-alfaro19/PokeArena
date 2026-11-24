package cr.ac.tec.pokearena.domain.repository;

import java.util.List;

import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.Location;

/**
 * Propósito: puerto de persistencia de ubicaciones (patrón Repository, sección 4.4).
 * Responsabilidades: guardar una ubicación nueva y devolver su identificador, y consultar el catálogo
 * disponible para preparar partidas.
 * Referencia: secciones 7.2, paso 10, y 7.3, paso 2.
 *
 * <p>Debe llamarse dentro del {@code UnitOfWork} que también guarda la clave de idempotencia. Guardar
 * una ubicación solo agrega una fila a la tabla location: no toca ninguna otra (criterio C.2, "una
 * ubicación creada mientras hay partidas en curso no altera esas partidas").
 */
public interface LocationRepository {

    /** Guarda la ubicación y devuelve su identificador. */
    long save(Location location);

    /** Ubicaciones disponibles y activas, ordenadas por identificador. */
    List<AvailableLocation> findAllAvailable();
}
