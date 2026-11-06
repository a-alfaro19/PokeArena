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

import cr.ac.tec.pokearena.api.dto.LocationResponse;
import cr.ac.tec.pokearena.api.security.RequiresRole;
import cr.ac.tec.pokearena.api.web.IdempotencyKeyHeader;
import cr.ac.tec.pokearena.application.dto.CreateLocationCommand;
import cr.ac.tec.pokearena.application.dto.IdempotentResult;
import cr.ac.tec.pokearena.application.dto.LocationDetails;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.usecase.CreateLocationUseCase;
import cr.ac.tec.pokearena.application.usecase.RequestHash;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;

/**
 * Propósito: API de ubicaciones del administrador (historia 2.2.1; sección 5.1).
 * Responsabilidades: traducir {@code POST /api/v1/ubicaciones} (multipart) a un comando del caso de uso
 * y su resultado a la respuesta 201 con la ubicación. No contiene reglas: valida el caso de uso.
 * Referencia: sección 7.2 (pasos 1 a 3 y 11) y Anexo A.2 (mensajes 2 a 5, 21 y 22).
 *
 * <p>Igual que en cartas, todos los campos se reciben como opcionales y como texto: así un campo
 * ausente o mal escrito lo informa el dominio con su error tipado, su {@code field} y sus
 * {@code allowedValues}. El efecto llega como un texto JSON y lo interpreta el caso de uso en el
 * paso 6, para respetar el orden de los errores.
 */
@RestController
@RequestMapping("/api/v1/ubicaciones")
@RequiresRole(Role.ADMIN)
public class LocationController {

    private final CreateLocationUseCase createLocation;

    public LocationController(CreateLocationUseCase createLocation) {
        this.createLocation = createLocation;
    }

    /**
     * Crea una ubicación.
     *
     * <p>Orden: el interceptor ya autorizó (paso 2); {@code IdempotencyKeyHeader} valida la clave al
     * resolver el parámetro (paso 3); el resto lo valida el caso de uso.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LocationResponse> create(AuthenticatedUser user, IdempotencyKeyHeader idempotencyKey,
            @RequestParam(name = LocationFieldRules.NAME_FIELD, required = false) String name,
            @RequestParam(name = LocationFieldRules.DESCRIPTION_FIELD, required = false) String description,
            @RequestParam(name = LocationFieldRules.CARD_LIMIT_FIELD, required = false) String cardLimit,
            @RequestParam(name = LocationFieldRules.ENERGY_MODE_FIELD, required = false) String extraEnergyMode,
            @RequestParam(name = LocationFieldRules.ENERGY_AMOUNT_FIELD, required = false) String extraEnergyAmount,
            @RequestParam(name = LocationFieldRules.EFFECT_FIELD, required = false) String effect,
            @RequestPart(name = LocationFieldRules.IMAGE_FIELD, required = false) MultipartFile image)
            throws IOException {

        byte[] imageBytes = image == null ? null : image.getBytes();

        // El hash cubre todos los campos tal como llegaron, el efecto incluido, y la imagen (sección 5.4).
        String requestHash = RequestHash.builder()
                .field(LocationFieldRules.NAME_FIELD, name)
                .field(LocationFieldRules.DESCRIPTION_FIELD, description)
                .field(LocationFieldRules.CARD_LIMIT_FIELD, cardLimit)
                .field(LocationFieldRules.ENERGY_MODE_FIELD, extraEnergyMode)
                .field(LocationFieldRules.ENERGY_AMOUNT_FIELD, extraEnergyAmount)
                .field(LocationFieldRules.EFFECT_FIELD, effect)
                .file(LocationFieldRules.IMAGE_FIELD, imageBytes)
                .build();

        IdempotentResult<LocationDetails> result = createLocation.execute(
                idempotencyKey.toRequest(user.playerId(), requestHash, HttpStatus.CREATED.value()),
                new CreateLocationCommand(name, description, cardLimit, extraEnergyMode, extraEnergyAmount, effect,
                        imageBytes));

        // Paso 11: 201 con la ubicación (o la misma respuesta guardada, si es un reintento).
        return ResponseEntity.status(result.status()).body(LocationResponse.from(result.body()));
    }
}
