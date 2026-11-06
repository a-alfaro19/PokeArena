package cr.ac.tec.pokearena.api.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cr.ac.tec.pokearena.api.dto.CardResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.dto.CardDetails;
import cr.ac.tec.pokearena.application.dto.CreateCardCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.CreateCardUseCase;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: API de cartas del administrador (historia 2.1.1; sección 5.1).
 * Responsabilidades: traducir {@code POST /api/v1/cartas} (multipart) a un comando del caso de uso y
 * su resultado a la respuesta 201 con la ficha. No contiene reglas: valida el caso de uso.
 * Referencia: sección 7.1 (pasos 1 a 3 y 11) y Anexo A.1 (mensajes 2 a 5, 21 y 22).
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

    public CardController(CreateCardUseCase createCard) {
        this.createCard = createCard;
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
}
