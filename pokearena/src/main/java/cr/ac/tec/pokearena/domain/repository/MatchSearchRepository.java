package cr.ac.tec.pokearena.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.MatchSearch;

/** Puerto de persistencia de las búsquedas de partida (US 47, A.11 y A.12). */
public interface MatchSearchRepository {

    /** Renueva una espera vigente o la expira si venció. Solo devuelve búsquedas del titular. */
    Optional<MatchSearch> touchAndFind(UUID id, UUID playerId, Instant now);

    /** Lee la búsqueda del titular con bloqueo exclusivo hasta el fin de la transacción (A.12). */
    Optional<MatchSearch> lockById(UUID id, UUID playerId);

    /** Cancela la búsqueda en espera previamente bloqueada y devuelve la fila actualizada. */
    MatchSearch markCancelled(UUID id, Instant now);
}
