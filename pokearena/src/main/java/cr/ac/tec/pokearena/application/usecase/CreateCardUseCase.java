package cr.ac.tec.pokearena.application.usecase;

import java.time.LocalDateTime;
import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.CreateCardCommand;
import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.repository.CardRepository;

public class CreateCardUseCase {
    private final CardRepository cardRepository;

    public CreateCardUseCase(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public Card execute(CreateCardCommand command) {
        // 1. Manejo de Idempotencia: Verificar solicitud previa
        Optional<Card> existingCard = cardRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existingCard.isPresent()) {
            return existingCard.get();
        }

        // 2. Validaciones de limites del negocio
        if (command.cost() < 0 || command.cost() > 10) {
            throw new IllegalArgumentException("El costo debe estar entre 0 y 10");
        }
        if (command.power() < -20 || command.power() > 20) {
            throw new IllegalArgumentException("El poder debe estar entre -20 y 20");
        }

        // 3. Validar existencia de habilidad
        boolean abilityExists = cardRepository.existsAbilityByCode(command.abilityCode());
        if (!abilityExists) {
            throw new IllegalArgumentException("La habilidad especificada no existe");
        }

        // 4. Procesamiento e insumos de imágenes
        String largeImgUrl = cardRepository.saveImage(command.imageBytes(), "large");
        String smallImgUrl = cardRepository.saveImage(command.imageBytes(), "small");

        // 5. Asignación de código correlativo y persistencia en transacción
        String nextCode = cardRepository.generateNextCodeForType(command.type());

        Card newCard = new Card(
                nextCode,
                command.name(),
                command.cost(),
                command.power(),
                command.type(),
                command.rarity(),
                command.abilityCode(),
                largeImgUrl,
                smallImgUrl,
                "DISPONIBLE",
                LocalDateTime.now());

        return cardRepository.saveInTransaction(newCard, command.idempotencyKey());
    }
}
