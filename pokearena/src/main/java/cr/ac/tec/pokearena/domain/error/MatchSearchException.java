package cr.ac.tec.pokearena.domain.error;

import java.util.UUID;

/**
 * Errores al consultar una búsqueda, cancelarla o aceptar jugar contra el bot.
 * Cada fábrica corresponde a un resultado de la historia 4.1.2 (sección 5.3).
 */
public final class MatchSearchException extends DomainException {

    private MatchSearchException(ErrorType type, String message) {
        super(type, message);
    }

    public static MatchSearchException notFound() {
        return new MatchSearchException(ErrorType.SEARCH_NOT_FOUND,
                "La búsqueda no existe.");
    }

    public static MatchSearchException alreadyMatched(UUID matchId) {
        return new MatchSearchException(ErrorType.SEARCH_ALREADY_MATCHED,
                "La partida asignada es " + matchId + ".");
    }

    public static MatchSearchException notOpen() {
        return new MatchSearchException(ErrorType.SEARCH_NOT_OPEN,
                "La búsqueda ya está cerrada.");
    }

    public static MatchSearchException botOfferNotAvailable() {
        return new MatchSearchException(ErrorType.BOT_OFFER_NOT_AVAILABLE,
                "La oferta del bot todavía no está disponible.");
    }

    public static MatchSearchException preparationFailed() {
        return new MatchSearchException(ErrorType.MATCH_PREPARATION_FAILED,
                "No hay ubicaciones disponibles. La búsqueda continúa.");
    }
}
