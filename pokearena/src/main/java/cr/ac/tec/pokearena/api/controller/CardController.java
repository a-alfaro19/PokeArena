package cr.ac.tec.pokearena.api.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cr.ac.tec.pokearena.api.dto.CardResponse;
import cr.ac.tec.pokearena.api.dto.PageResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CreateCardCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.SearchCardsCommand;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.CreateCardUseCase;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.application.usecase.SearchCardsUseCase;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: API de cartas del administrador (historias 2.1.1 y 2.1.2; sección 5.1).
 * Responsabilidades: traducir {@code POST /api/v1/cartas} (multipart) a un comando de Crear carta y su
 * resultado a la respuesta 201 con la ficha; y {@code GET /api/v1/cartas} a un comando de Buscar carta
 * y su resultado a la respuesta 200 con la página. No contiene reglas: valida el caso de uso.
 * Referencia: sección 7.1 (pasos 1 a 3 y 11), Anexo A.1 (mensajes 2 a 5, 21 y 22), sección 7.3 y
 * Anexo A.3 (mensajes 2 a 5 y 13 a 15).
 *
 * <p>Las dos operaciones son solo para administradores: {@code @RequiresRole(ADMIN)} en la clase las
 * cubre a ambas, con la misma autorización (401 sin sesión, 403 sin el rol).
 *
 * <p>Todos los campos se reciben como opcionales y como texto: así un campo ausente o mal escrito lo
 * informa el dominio con su error tipado, su {@code field} y sus {@code allowedValues}, en lugar de
 * un error genérico del enlace de parámetros de Spring.
 */
@RestController
@RequestMapping("/api/v1/cartas")
@RequiresRole(Role.ADMIN)
public class CardController {

    private final CreateCardUseCase createCard;
    private final SearchCardsUseCase searchCards;

    public CardController(CreateCardUseCase createCard, SearchCardsUseCase searchCards) {
        this.createCard = createCard;
        this.searchCards = searchCards;
    }

    /**
     * Crea una carta.
     *
     * <p>Orden: el interceptor ya autorizó (paso 2); {@code IdempotencyKeyHeader} valida la clave al
     * resolver el parámetro (paso 3); el resto lo valida el caso de uso.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CardResponse> create(AuthenticatedUser user, IdempotencyKeyHeader idempotencyKey,
            @RequestParam(name = CardFieldRules.NAME_FIELD, required = false) String name,
            @RequestParam(name = CardFieldRules.COST_FIELD, required = false) String cost,
            @RequestParam(name = CardFieldRules.POWER_FIELD, required = false) String power,
            @RequestParam(name = CardFieldRules.TYPE_FIELD, required = false) String type,
            @RequestParam(name = CardFieldRules.RARITY_FIELD, required = false) String rarity,
            @RequestParam(name = CardFieldRules.ABILITY_FIELD, required = false) String ability,
            @RequestPart(name = CardFieldRules.IMAGE_FIELD, required = false) MultipartFile image) throws IOException {

        byte[] imageBytes = image == null ? null : image.getBytes();

        // El hash cubre todos los campos y el contenido de la imagen (sección 5.4).
        String requestHash = RequestHash.builder()
                .field(CardFieldRules.NAME_FIELD, name)
                .field(CardFieldRules.COST_FIELD, cost)
                .field(CardFieldRules.POWER_FIELD, power)
                .field(CardFieldRules.TYPE_FIELD, type)
                .field(CardFieldRules.RARITY_FIELD, rarity)
                .field(CardFieldRules.ABILITY_FIELD, ability)
                .file(CardFieldRules.IMAGE_FIELD, imageBytes)
                .build();

        IdempotentResult<CardDetails> result = createCard.execute(
                idempotencyKey.toRequest(user.playerId(), requestHash, HttpStatus.CREATED.value()),
                new CreateCardCommand(name, cost, power, type, rarity, ability, imageBytes));

        // Paso 11: 201 con la ficha (o la misma respuesta guardada, si es un reintento).
        return ResponseEntity.status(result.status()).body(CardResponse.from(result.body()));
    }

    /**
     * Busca cartas con criterios combinables y paginación por cursor (sección 7.3).
     *
     * <p>Recibe TODOS los parámetros de la consulta, con sus repeticiones, sin enlazarlos uno por uno:
     * así un criterio desconocido, repetido o mal escrito lo informa el dominio con
     * INVALID_SEARCH_CRITERIA y el criterio en {@code field}, y un {@code limit} o {@code cursor}
     * inválido, la paginación con INVALID_PAGINATION. El interceptor ya autorizó antes (mensajes 3 y 4).
     */
    @GetMapping
    public PageResponse<CardResponse> search(@RequestParam MultiValueMap<String, String> parameters) {
        // Mensaje 5: buscarCartas(criterios, cursor, límite). Mensajes 13 y 14: 200 con las cartas y el cursor.
        return PageResponse.from(searchCards.execute(new SearchCardsCommand(parameters)), CardResponse::from);
    }
}
