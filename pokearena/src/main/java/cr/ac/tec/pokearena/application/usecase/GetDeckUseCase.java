package cr.ac.tec.pokearena.application.usecase;

import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.DeckDetails;
import cr.ac.tec.pokearena.application.dto.GetDeckCommand;
import cr.ac.tec.pokearena.application.dto.PageRequest;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.model.Deck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;

/**
 * Propósito: caso de uso "Consultar un deck" de la historia 3.1.3.
 * Responsabilidades: devolver un deck del jugador de la sesión con sus cartas; si no existe o es de otro
 * jugador, {@code DECK_NOT_FOUND} (404), con el mismo mensaje para ambos casos.
 * Referencia: sección 7.1 ("Listar y consultar"), sección 5.2 ("ante un id ajeno o inexistente se responde
 * igual") y Anexo A.8, mensajes 15 a 26; criterio C.7-R4.
 *
 * <h2>Misma plantilla que los demás casos de uso</h2>
 * <ol>
 *   <li><b>Autorizar</b> (mensaje 17): en la API, {@code @RequiresRole({PLAYER, ADMIN})}.</li>
 *   <li><b>Idempotencia:</b> no aplica; es una lectura (GET).</li>
 *   <li><b>Validar:</b> un id que no es un número no puede existir: es {@code DECK_NOT_FOUND}.</li>
 *   <li><b>Ejecutar</b> (mensajes 19, 20 y 24): una lectura del deck con sus cartas, sin {@code UnitOfWork}.</li>
 *   <li><b>Responder</b> (mensajes 21 y 22, o 25 y 26): 200 con el deck, o 404.</li>
 * </ol>
 */
public class GetDeckUseCase {

    /** Mismo mensaje para un deck inexistente y para uno ajeno: no se revela si existe. */
    static final String NOT_FOUND_MESSAGE = "El deck no existe.";

    private final DeckRepository decks;

    public GetDeckUseCase(DeckRepository decks) {
        this.decks = decks;
    }

    /** El deck con sus cartas. */
    public DeckDetails execute(GetDeckCommand command) {
        // Mensajes 16 a 18 del diagrama A.8: la interfaz pidió GET /api/v1/decks/{id}, AuthorizationInterceptor
        // verificó el token y el rol, y DeckController llamó a este caso de uso.
        Optional<Long> deckId = parseId(command.deckId());

        // Mensajes 19 y 20 del diagrama A.8: DeckRepository.findByIdAndPlayer, el deck con sus 12 cartas.
        // Mensaje 24 del diagrama A.8: vacío si no existe o si es de otro jugador.
        Optional<Deck> deck = deckId.flatMap(id -> decks.findByIdAndPlayer(id, command.playerId()));

        // Mensajes 21 y 22 del diagrama A.8: DeckDetails para la API, que responde 200.
        // Mensajes 25 y 26 del diagrama A.8: DECK_NOT_FOUND, que la API responde con 404.
        return deck.map(DeckDetails::of).orElseThrow(() -> DeckException.notFound(NOT_FOUND_MESSAGE));
    }

    /** Un id que no es un entero positivo no puede ser de ningún deck (los bigserial empiezan en 1). */
    private static Optional<Long> parseId(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(text);
            return id > PageRequest.FROM_START ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
