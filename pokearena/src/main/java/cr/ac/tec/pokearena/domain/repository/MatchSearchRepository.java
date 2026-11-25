package cr.ac.tec.pokearena.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.MatchContext;
import cr.ac.tec.pokearena.domain.model.MatchSearch;

/** Puerto de persistencia de las búsquedas de partida (US 46 y US 47). */
public interface MatchSearchRepository {

    /** Busca la espera del jugador en cualquier contexto (A.9). */
    Optional<MatchSearch> findOpenByPlayer(UUID playerId);

    /**
     * Registra la espera dentro de la transacción del llamador (A.9).
     * Devuelve vacío si el jugador ya tiene una búsqueda abierta.
     */
    Optional<MatchSearch> insertWaiting(UUID playerId, long deckId, MatchContext context, int elo, Instant now);

    /** Renueva una espera vigente o la expira si venció. Solo devuelve búsquedas del titular. */
    Optional<MatchSearch> touchAndFind(UUID id, UUID playerId, Instant now);

    /** Expira esperas anteriores al corte exclusivo y registra el instante del barrido (A.10, mensaje 4). */
    int expireStale(Instant cutoff, Instant now);

    /** Bloquea la espera elegible más antigua posterior al cursor de este ciclo (A.10). */
    Optional<MatchSearch> lockOldestWaiting(Instant now, Instant cutoff, MatchSearch after);

    /** Bloquea un lote de posibles rivales sin esperar por filas de otro emparejador (A.10). */
    List<MatchSearch> lockCandidates(MatchSearch anchor, Instant now, Instant cutoff, int maxRange, int limit);

    /** Registra el fallo de ubicaciones solo en las búsquedas que sigan abiertas (A.10). */
    int markNotice(UUID first, UUID second, Instant retryAt);

    /** Lee la búsqueda del titular con bloqueo exclusivo hasta el fin de la transacción (A.12). */
    Optional<MatchSearch> lockById(UUID id, UUID playerId);

    /** Asigna una partida a la fila en espera previamente bloqueada. */
    MatchSearch markMatched(UUID id, UUID matchId, Instant now);

    /** Cancela la búsqueda en espera previamente bloqueada y devuelve la fila actualizada. */
    MatchSearch markCancelled(UUID id, Instant now);
}
