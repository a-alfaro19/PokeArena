package cr.ac.tec.pokearena.application.dto;

import java.util.Map;

// Conserva únicamente los datos de catálogo necesarios para revisar una carta.
public record CardSearchItem(String code, String name, String largeImageUrl, String smallImageUrl,
        String imageStatus, int cost, int power, String type, String rarity, AbilityDetails ability) {
    // Los metadatos desconocidos permanecen explícitamente nulos, sin inventar reglas del juego.
    public record AbilityDetails(String code, String name, String trigger, String effect,
            Map<String, Object> parameters) {
    }
}
