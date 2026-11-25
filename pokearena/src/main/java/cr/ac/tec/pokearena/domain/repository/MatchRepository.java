package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.NewMatch;

/**
 * Propósito: puerto de persistencia de partidas.
 * Responsabilidades: guardar el agregado inicial, localizar la partida activa de un jugador y leer una partida
 * solo si el jugador participa en ella.
 * Referencia: sección 7.3, pasos 6 y 7; Anexos A.9, A.14 y A.15.
 */
public interface MatchRepository extends ActiveMatchLookup {

    /** Guarda el agregado completo dentro de la transacción activa y devuelve el id generado. */
    UUID save(NewMatch match);

    /** Devuelve la partida completa solo para uno de sus participantes; otros ids se comportan como inexistentes. */
    Optional<Match> findByIdForPlayer(UUID matchId, UUID playerId);
}
