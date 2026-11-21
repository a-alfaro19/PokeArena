package cr.ac.tec.pokearena.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.CreateDeckCommand;
import cr.ac.tec.pokearena.application.dto.DeckDetails;
import cr.ac.tec.pokearena.application.dto.IdempotentRequest;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.error.ErrorType;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.NewDeck;
import cr.ac.tec.pokearena.domain.model.OwnedCard;
import cr.ac.tec.pokearena.domain.model.StoredCard;
import cr.ac.tec.pokearena.domain.model.StoredDeck;
import cr.ac.tec.pokearena.domain.repository.DeckRepository;
import cr.ac.tec.pokearena.domain.repository.PlayerCardRepository;
import cr.ac.tec.pokearena.domain.rule.DeckRules;

/**
 * Propósito: caso de uso de la historia 3.1.3 "Crear Deck".
 * Responsabilidades: orquestar idempotencia, validación de la forma, comprobación de posesión y
 * persistencia del deck, en el orden del diagrama de secuencia.
 * Referencia: sección 7.1 (tabla de pasos) y Anexo A.7 (los números de mensaje son los de la numeración
 * automática del diagrama).
 *
 * <h2>Misma plantilla que los casos de uso de creación del Sprint 2</h2>
 * <ol>
 *   <li><b>Autorizar</b> (mensajes 9 y 10): en la API, {@code @RequiresRole({PLAYER, ADMIN})}.</li>
 *   <li><b>Idempotencia</b> (mensajes 12 a 15): {@link IdempotencyGuard#findPrevious}.</li>
 *   <li><b>Validar todo antes de guardar nada</b> (mensajes 16 a 21): hora del {@link Clock}, forma con
 *       {@link DeckRules}, una sola consulta de las cartas poseídas y la posesión.</li>
 *   <li><b>Ejecutar en UnitOfWork</b> (mensajes 22 a 33): el deck, sus cartas y la clave de idempotencia en
 *       una sola transacción, con sus dos caminos de error (nombre repetido y conflicto de clave).</li>
 *   <li><b>Responder</b> (mensaje 34): la API responde 201 con el deck.</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>La hora de creación viene del puerto {@link Clock} y entra a {@code DeckRules} por parámetro: el
 *       dominio no lee el reloj (regla 4 de 4.2).</li>
 *   <li>La respuesta se arma con las cartas que ya trajo la comprobación de posesión (mensaje 20): no se
 *       vuelve a leer el deck.</li>
 *   <li>Un error nunca se guarda como respuesta idempotente: si algo falla dentro de la transacción, el
 *       {@code UnitOfWork} revierte el deck, sus cartas y la clave juntos.</li>
 *   <li>Nombre repetido y reintentos: ver {@link #execute}.</li>
 * </ul>
 */
public class CreateDeckUseCase {

    private final IdempotencyGuard idempotency;
    private final PlayerCardRepository playerCards;
    private final DeckRepository decks;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateDeckUseCase(IdempotencyGuard idempotency, PlayerCardRepository playerCards, DeckRepository decks,
            UnitOfWork unitOfWork, Clock clock) {
        this.idempotency = idempotency;
        this.playerCards = playerCards;
        this.decks = decks;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /**
     * Crea un deck.
     *
     * <p><b>Por qué se consulta la idempotencia después de un nombre repetido.</b> Dos envíos de la MISMA
     * solicitud (por ejemplo, un reintento de red que llega mientras la primera aún no confirma) traen el
     * mismo nombre. La segunda espera en la restricción {@code uq_deck_player_name} hasta que la primera
     * confirma el deck <i>y su clave</i>, y entonces recibe {@code DECK_NAME_TAKEN} antes de llegar a
     * registrar su clave (no un {@link IdempotencyConflictException}). Si solo se propagara ese 409, un
     * reintento recibiría "nombre en uso" en lugar de su 201 (criterio C.7-A1). Por eso, ya fuera de la
     * transacción revertida (así la lectura ve lo confirmado), se busca la clave: misma huella, se repite la
     * respuesta guardada; otra huella, {@code IDEMPOTENCY_KEY_REUSED}; nada guardado, el nombre lo usa otro
     * deck y se propaga {@code DECK_NAME_TAKEN}. Es la rama "Nombre repetido" del diagrama A.7 (mensajes 28 a 30);
     * el conflicto de clave es la otra rama (mensajes 31 a 33).
     *
     * @param request datos de idempotencia (clave del jugador autenticado y huella del contenido)
     * @param command nombre y códigos tal como llegaron, y el jugador de la sesión
     * @return el deck, nuevo o repetido de una solicitud anterior con la misma clave
     */
    public IdempotentResult<DeckDetails> execute(IdempotentRequest request, CreateDeckCommand command) {
        // Mensajes 8 a 11 del diagrama A.7: la interfaz envió el POST, AuthorizationInterceptor verificó el token
        // y el rol PLAYER o ADMIN, IdempotencyKeyHeader validó la clave y DeckController llamó a este caso de uso.

        // Mensajes 12 a 15 del diagrama A.7: IdempotencyGuard.findPrevious busca una solicitud previa con la
        // clave en IdempotencyStore. Misma huella: se repite la respuesta guardada; otra: IDEMPOTENCY_KEY_REUSED.
        Optional<IdempotentResult<DeckDetails>> previous = idempotency.findPrevious(request, DeckDetails.class);
        if (previous.isPresent()) {
            return previous.get();
        }

        // Mensajes 16 y 17 del diagrama A.7: Clock.now(), la hora de creación (el dominio no lee el reloj).
        Instant now = clock.now();

        // Mensaje 18 del diagrama A.7: DeckRules.validateShape, en orden: nombre, lista, cantidad exacta,
        // códigos no vacíos y repetidas.
        NewDeck deck = DeckRules.validateShape(command.playerId(), command.name(), command.cards(), now);

        // Mensajes 19 y 20 del diagrama A.7: PlayerCardRepository.findOwnedCards, UNA consulta por la clave
        // (player_id, card_code) que devuelve las cartas poseídas con sus datos.
        List<Card> owned = playerCards.findOwnedCards(command.playerId(), deck.cardCodes()).stream()
                .map(OwnedCard::stored)
                .map(StoredCard::card)
                .toList();

        // Mensaje 21 del diagrama A.7: DeckRules.checkOwnership, todas las cartas deben estar poseídas.
        DeckRules.checkOwnership(deck.cardCodes(), owned.stream().map(card -> card.code().value()).toList());

        try {
            // Mensaje 22 del diagrama A.7: UnitOfWork.execute, una sola transacción.
            DeckDetails created = unitOfWork.execute(() -> {
                // Mensajes 23 y 24 del diagrama A.7: DeckRepository.save guarda el deck y sus 12 cartas, y
                // devuelve su id y su hora tal como quedó guardada. Lanza DECK_NAME_TAKEN si el jugador ya tiene
                // un deck con ese nombre (rama "Nombre repetido").
                StoredDeck stored = decks.save(deck);
                DeckDetails details = DeckDetails.of(stored, owned);
                // Mensajes 25 y 26 del diagrama A.7: IdempotencyGuard.record guarda la clave y la respuesta en
                // IdempotencyStore, en la misma transacción que el deck.
                idempotency.record(request, details);
                return details;
            });
            // Mensaje 27 del diagrama A.7: commit. Mensaje 34: el resultado para la API (201 con el deck).
            return IdempotentResult.executed(request, created);
        } catch (IdempotencyConflictException conflict) {
            // Mensajes 31 a 33 del diagrama A.7 (rama "Conflicto de idempotencia"): otra solicitud con la misma
            // clave guardó primero. La transacción ya se revirtió; se repite su respuesta (o IDEMPOTENCY_KEY_REUSED).
            return idempotency.resolveConflict(request, DeckDetails.class);
        } catch (DeckException nameTaken) {
            if (nameTaken.type() != ErrorType.DECK_NAME_TAKEN) {
                throw nameTaken;
            }
            // Mensajes 28 a 30 del diagrama A.7 (rama "Nombre repetido"): fuera de la transacción revertida, se
            // consulta la clave. Ver el encabezado de este método: puede ser el reintento de esta misma solicitud.
            return idempotency.findPrevious(request, DeckDetails.class).orElseThrow(() -> nameTaken);
        }
    }
}
