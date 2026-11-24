package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: errores de dominio de la consulta de partidas.
 * Responsabilidades: producir un error tipado cuando la partida no existe o no pertenece al jugador, sin
 * revelar cuál de esas condiciones ocurrió.
 * Referencia: sección 5.3 (MATCH_NOT_FOUND) y Anexo A.15.
 */
public final class MatchException extends DomainException {

    private MatchException(ErrorType type, String message) {
        super(type, message);
    }

    public static MatchException notFound(String message) {
        return new MatchException(ErrorType.MATCH_NOT_FOUND, message);
    }
}
