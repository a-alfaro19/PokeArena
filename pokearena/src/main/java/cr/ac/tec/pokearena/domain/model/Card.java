package cr.ac.tec.pokearena.domain.model;

import java.time.LocalDateTime;

public class Card {
    private final String code;
    private final String name;
    private final int cost;
    private final int power;
    private final CardType type;
    private final CardRarity rarity;
    private final String abilityCode;
    private final String largeImageUrl;
    private final String smallImageUrl;
    private final String imageStatus;
    private final LocalDateTime createdAt;

    public Card(String code, String name, int cost, int power, CardType type, CardRarity rarity, String abilityCode,
            String largeImageUrl, String smallImageUrl, String imageStatus, LocalDateTime createdAt) {
        this.code = code;
        this.name = name;
        this.cost = cost;
        this.power = power;
        this.type = type;
        this.rarity = rarity;
        this.abilityCode = abilityCode;
        this.largeImageUrl = largeImageUrl;
        this.smallImageUrl = smallImageUrl;
        this.imageStatus = imageStatus;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCost() {
        return cost;
    }

    public int getPower() {
        return power;
    }

    public CardType getType() {
        return type;
    }

    public CardRarity getRarity() {
        return rarity;
    }

    public String getAbilityCode() {
        return abilityCode;
    }

    public String getLargeImageUrl() {
        return largeImageUrl;
    }

    public String getSmallImageUrl() {
        return smallImageUrl;
    }

    public String getImageStatus() {
        return imageStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
