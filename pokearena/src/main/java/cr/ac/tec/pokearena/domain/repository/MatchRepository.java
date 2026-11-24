package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;
import java.util.UUID;

import cr.ac.tec.pokearena.domain.model.Match;
import cr.ac.tec.pokearena.domain.model.NewMatch;

/**
 * Propósito: puerto de persistencia del estado de las partidas (tablas game_match*).
 * Responsabilidades: guardar el estado inicial atómicamente, detectar una partida activa de un jugador
 * y cargar una partida solo cuando el solicitante ocupa uno de sus asientos.
 * Referencia: secciones 6.2 y 7.3; Anexos A.14 y A.15.
 */
public interface MatchRepository {

    /**
     * Guarda la partida y todos sus asientos, ubicaciones y cartas dentro de la transacción del llamador.
     *
     * @return identificador de la partida guardada
     */
    UUID save(NewMatch match);

    /** Identificador de la partida activa del jugador, si existe. */
    Optional<UUID> findActiveMatchId(UUID playerId);

    /**
     * Carga la partida si el jugador ocupa uno de sus asientos.
     *
     * @return vacío tanto si la partida no existe como si no pertenece al jugador
     */
    Optional<Match> findByIdForPlayer(UUID matchId, UUID playerId);
}
