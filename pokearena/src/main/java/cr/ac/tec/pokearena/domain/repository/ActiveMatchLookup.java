package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;
import java.util.UUID;

/** Lectura de la partida activa requerida por el alta de búsqueda (A.9). */
public interface ActiveMatchLookup {
    Optional<UUID> findActiveMatchId(UUID playerId);
}
