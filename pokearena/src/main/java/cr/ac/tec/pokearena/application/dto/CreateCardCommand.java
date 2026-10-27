package cr.ac.tec.pokearena.application.dto;

import cr.ac.tec.pokearena.domain.model.CardRarity;
import cr.ac.tec.pokearena.domain.model.CardType;

public record CreateCardCommand(
        String name,
        int cost,
        int power,
        CardType type,
        CardRarity rarity,
        String abilityCode,
        byte[] imageBytes,
        String imageMimeType,
        String idempotencyKey) {
}
