package cr.ac.tec.pokearena.domain.error;

import java.util.UUID;

/**
 * Errores de alta, consulta, salida y aceptación del bot (sección 5.3).
 */
public final class MatchSearchException extends DomainException {

    private MatchSearchException(ErrorType type, String message) {
        super(type, message);
    }

    /** Los errores de alta indican los datos que el jugador necesita para continuar (A.9). */
    public static MatchSearchException invalidDeck(String reason) {
        return new MatchSearchException(ErrorType.INVALID_DECK, reason);
    }

    public static MatchSearchException activeMatchExists(UUID matchId) {
        return new MatchSearchException(ErrorType.ACTIVE_MATCH_EXISTS, "Ya tienes una partida activa: " + matchId + ".");
    }

    public static MatchSearchException alreadyOpen(UUID searchId, long deckId) {
        return new MatchSearchException(ErrorType.SEARCH_ALREADY_OPEN,
                "Ya tienes la búsqueda " + searchId + " abierta con el deck " + deckId + ".");
    }

    public static MatchSearchException insufficientCubes(long balance, long minimum) {
        return new MatchSearchException(ErrorType.INSUFFICIENT_CUBES,
                "Tienes " + balance + " cubos y necesitas al menos " + minimum + ". Compra cubos para continuar.");
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

    public static MatchSearchException insufficientBotCards() {
        return new MatchSearchException(ErrorType.MATCH_PREPARATION_FAILED,
                "No hay suficientes cartas Comunes disponibles para el deck del bot. La búsqueda continúa.");
    }
}
