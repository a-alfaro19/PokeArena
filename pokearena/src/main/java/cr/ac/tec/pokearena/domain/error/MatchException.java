package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: representar errores de consulta de una partida.
 * Responsabilidades: exponer un error seguro que no revela si una partida ajena existe.
 * Referencia: sección 5.3, historia 5.1.1.
 */
public final class MatchException extends DomainException {

    private MatchException(ErrorType type, String message) {
        super(type, message);
    }

    public static MatchException notFound() {
        return new MatchException(ErrorType.MATCH_NOT_FOUND, "La partida no existe.");
    }
}
