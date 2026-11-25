package cr.ac.tec.pokearena.domain.model;

/** Elo y saldo que se leen juntos al solicitar una búsqueda clasificatoria (A.9). */
public record MatchmakingPlayer(int elo, long cubeBalance) {
}
