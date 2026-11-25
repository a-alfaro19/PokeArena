package cr.ac.tec.pokearena.domain.rule;

import cr.ac.tec.pokearena.domain.error.MatchSearchException;

/** Regla de saldo mínimo para ingresar a la búsqueda clasificatoria (A.9). */
public record MatchSearchRules(long maxBet) {
    public MatchSearchRules {
        // Un mínimo inválido debe fallar al configurar la aplicación.
        if (maxBet < 1) {
            throw new IllegalArgumentException("La apuesta máxima debe ser positiva.");
        }
    }

    public void checkCubes(long balance) {
        // El alta solo valida el saldo. Los movimientos de cubos corresponden a la partida.
        if (balance < maxBet) {
            throw MatchSearchException.insufficientCubes(balance, maxBet);
        }
    }
}
