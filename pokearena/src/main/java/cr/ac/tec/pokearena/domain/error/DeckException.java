package cr.ac.tec.pokearena.domain.error;

/**
 * Propósito: errores de dominio de la historia 3.1.3 "Crear Deck" (sección 5.3 del Sprint 3).
 * Responsabilidades: ofrecer una fábrica por cada tipo de error de deck, con el campo afectado cuando
 * aplica. El mensaje lo arma quien detecta el error ({@code DeckRules} o el caso de uso), porque es él
 * quien conoce la cantidad actual o los códigos que debe listar el {@code detail}.
 * Referencia: sección 5.3 del Sprint 3 (tipos INVALID_DECK_SIZE, DUPLICATE_DECK_CARDS, CARD_NOT_OWNED,
 * DECK_NAME_TAKEN y DECK_NOT_FOUND) y criterios C.7-R1, R2, R3, R4 y A2.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Una sola familia para los cinco tipos, como {@link ImageValidationException} agrupa los de imagen:
 *       cada fábrica fija su tipo, así no se puede crear un error de deck con un tipo ajeno.</li>
 *   <li>{@code DECK_NOT_FOUND} no lleva campo: el mismo error sirve para un deck inexistente y para uno
 *       ajeno, sin revelar cuál de los dos es (sección 5.2).</li>
 * </ul>
 */
public final class DeckException extends DomainException {

    /** Campo del nombre del deck en la solicitud ({@code CreateDeckRequest.name}). */
    public static final String NAME_FIELD = "name";

    /** Campo de la lista de códigos de carta en la solicitud ({@code CreateDeckRequest.cards}). */
    public static final String CARDS_FIELD = "cards";

    private DeckException(ErrorType type, String message, String field) {
        super(type, message, field, null);
    }

    /** El deck no tiene exactamente la cantidad de cartas requerida; el mensaje indica la cantidad actual. */
    public static DeckException invalidSize(String message) {
        return new DeckException(ErrorType.INVALID_DECK_SIZE, message, CARDS_FIELD);
    }

    /** Hay cartas repetidas; el mensaje lista los códigos repetidos. */
    public static DeckException duplicateCards(String message) {
        return new DeckException(ErrorType.DUPLICATE_DECK_CARDS, message, CARDS_FIELD);
    }

    /** El deck incluye cartas que el jugador no posee; el mensaje lista los códigos. */
    public static DeckException cardNotOwned(String message) {
        return new DeckException(ErrorType.CARD_NOT_OWNED, message, CARDS_FIELD);
    }

    /** El jugador ya tiene un deck con ese nombre. */
    public static DeckException nameTaken(String message) {
        return new DeckException(ErrorType.DECK_NAME_TAKEN, message, NAME_FIELD);
    }

    /** El deck no existe o no pertenece al jugador (mismo error para ambos casos). */
    public static DeckException notFound(String message) {
        return new DeckException(ErrorType.DECK_NOT_FOUND, message, null);
    }
}
