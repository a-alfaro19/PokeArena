package cr.ac.tec.pokearena.api.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cr.ac.tec.pokearena.api.dto.CreateDeckRequest;
import cr.ac.tec.pokearena.api.dto.DeckResponse;
import cr.ac.tec.pokearena.api.dto.DeckSummaryResponse;
import cr.ac.tec.pokearena.api.dto.PageResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.dto.CreateDeckCommand;
import cr.ac.tec.pokearena.application.dto.DeckDetails;
import cr.ac.tec.pokearena.application.dto.GetDeckCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.ListMyDecksCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.CreateDeckUseCase;
import cr.ac.tec.pokearena.application.usecase.GetDeckUseCase;
import cr.ac.tec.pokearena.application.usecase.ListMyDecksUseCase;
import cr.ac.tec.pokearena.application.usecase.Pagination;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.domain.error.DeckException;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: API de los decks del jugador (historia 3.1.3; sección 5.1 del Sprint 3).
 * Responsabilidades: traducir {@code POST /api/v1/decks}, {@code GET /api/v1/decks} y
 * {@code GET /api/v1/decks/{id}} a sus casos de uso, con el jugador tomado EXCLUSIVAMENTE de la sesión, y su
 * resultado a la respuesta HTTP. No contiene reglas: valida el dominio.
 * Referencia: sección 7.1; Anexo A.7 (mensajes 8, 11, 34 y 35) y A.8 (mensajes 2, 5, 12, 13, 16, 19, 22, 23, 26
 * y 27).
 *
 * <p>Decisiones:
 * <ul>
 *   <li>{@code @RequiresRole({PLAYER, ADMIN})} en CADA método (sección 5.1): la lista es explícita y sin
 *       jerarquía; un administrador también es jugador. 401 sin sesión, 403 sin uno de esos roles.</li>
 *   <li>El cuerpo del POST es opcional para Spring: un nombre o una lista ausentes los informa
 *       {@code DeckRules} con {@code MISSING_FIELD}, no un error genérico del enlace.</li>
 *   <li>La huella de idempotencia (sección 5.4) es el nombre y los códigos ORDENADOS: el orden en que se
 *       envían las cartas no cambia el deck ni la huella.</li>
 *   <li>El id de la ruta llega como texto: un id que no es un número es {@code DECK_NOT_FOUND} (404), igual
 *       que uno inexistente, en lugar de un 400 por el tipo del parámetro.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/decks")
public class DeckController {

    private final CreateDeckUseCase createDeck;
    private final ListMyDecksUseCase listMyDecks;
    private final GetDeckUseCase getDeck;

    public DeckController(CreateDeckUseCase createDeck, ListMyDecksUseCase listMyDecks, GetDeckUseCase getDeck) {
        this.createDeck = createDeck;
        this.listMyDecks = listMyDecks;
        this.getDeck = getDeck;
    }

    /**
     * Crea un deck.
     *
     * <p>Orden: el interceptor ya autorizó (mensajes 9 y 10 del diagrama A.7); {@code IdempotencyKeyHeader}
     * valida la clave al resolver el parámetro, antes de leer el cuerpo; el resto lo valida el caso de uso.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public ResponseEntity<DeckResponse> create(AuthenticatedUser user, IdempotencyKeyHeader idempotencyKey,
            @RequestBody(required = false) CreateDeckRequest body) {
        String name = body == null ? null : body.name();
        List<String> cards = body == null ? null : body.cards();

        // Mensaje 11 del diagrama A.7: execute(IdempotentRequest, CreateDeckCommand).
        IdempotentResult<DeckDetails> result = createDeck.execute(
                idempotencyKey.toRequest(user.playerId(), requestHash(name, cards), HttpStatus.CREATED.value()),
                new CreateDeckCommand(user.playerId(), name, cards));

        // Mensaje 35 del diagrama A.7: 201 con el deck (o la misma respuesta guardada, si es un reintento).
        return ResponseEntity.status(result.status()).body(DeckResponse.from(result.body()));
    }

    /** Lista mis decks, paginados por cursor. */
    @GetMapping
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public PageResponse<DeckSummaryResponse> list(AuthenticatedUser user,
            @RequestParam(name = Pagination.LIMIT, required = false) String limit,
            @RequestParam(name = Pagination.CURSOR, required = false) String cursor) {
        Map<String, List<String>> pagination = new HashMap<>();
        if (limit != null) {
            pagination.put(Pagination.LIMIT, List.of(limit));
        }
        if (cursor != null) {
            pagination.put(Pagination.CURSOR, List.of(cursor));
        }
        // Mensaje 5 del diagrama A.8: execute(ListMyDecksCommand). Mensaje 13: 200 con la página.
        return PageResponse.from(listMyDecks.execute(new ListMyDecksCommand(user.playerId(), pagination)),
                DeckSummaryResponse::from);
    }

    /** Consulta un deck mío con sus cartas. */
    @GetMapping("/{id}")
    @RequiresRole({Role.PLAYER, Role.ADMIN})
    public DeckResponse get(AuthenticatedUser user, @PathVariable(name = "id") String id) {
        // Mensaje 19 del diagrama A.8: execute(GetDeckCommand). Mensaje 23: 200 con el deck; mensaje 27: 404.
        return DeckResponse.from(getDeck.execute(new GetDeckCommand(user.playerId(), id)));
    }

    /**
     * Huella de la solicitud (sección 5.4): el nombre y los códigos ordenados, cada uno como un campo propio
     * ({@code cards[0]}, {@code cards[1]}, ...) para que ningún código pueda confundirse con dos. Una lista
     * ausente se distingue de una vacía; un código nulo cuenta como ausente.
     */
    private static String requestHash(String name, List<String> cards) {
        RequestHash.Builder hash = RequestHash.builder().field(DeckException.NAME_FIELD, name);
        if (cards == null) {
            return hash.field(DeckException.CARDS_FIELD, null).build();
        }
        List<String> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        hash.field(DeckException.CARDS_FIELD, sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            hash.field(DeckException.CARDS_FIELD + "[" + i + "]", sorted.get(i));
        }
        return hash.build();
    }
}
