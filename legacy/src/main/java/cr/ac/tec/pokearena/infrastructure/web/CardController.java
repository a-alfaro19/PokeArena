package cr.ac.tec.pokearena.infrastructure.web;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import cr.ac.tec.pokearena.application.dto.CreateCardCommand;
import cr.ac.tec.pokearena.application.usecase.CreateCardUseCase;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardRarity;
import cr.ac.tec.pokearena.domain.model.CardType;

@RestController
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CreateCardUseCase createCardUseCase;

    public CardController(CreateCardUseCase createCardUseCase) {
        this.createCardUseCase = createCardUseCase;
    }

    @PostMapping
    public ResponseEntity<Card> createCard(
            @RequestParam("name") String name,
            @RequestParam("cost") int cost,
            @RequestParam("power") int power,
            @RequestParam("type") CardType type,
            @RequestParam("rarity") CardRarity rarity,
            @RequestParam("abilityCode") String abilityCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("image") MultipartFile imageFile) throws IOException {

        CreateCardCommand command = new CreateCardCommand(
                name,
                cost,
                power,
                type,
                rarity,
                abilityCode,
                imageFile.getBytes(),
                imageFile.getContentType(),
                idempotencyKey);

        Card createdCard = createCardUseCase.execute(command);
        return new ResponseEntity<>(createdCard, HttpStatus.CREATED);
    }
}
