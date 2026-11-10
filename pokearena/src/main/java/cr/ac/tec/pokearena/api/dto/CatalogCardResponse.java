package cr.ac.tec.pokearena.api.dto;

import cr.ac.tec.pokearena.application.dto.CatalogCardDetails;

/**
 * Propósito: ficha HTTP del catálogo del jugador (8.3, C.6).
 * Responsabilidades: exponer imágenes y habilidad con el formato existente, más owned y copies.
 */
public record CatalogCardResponse(String code, String name, int cost, int power,
        CardResponse.CatalogValue type, CardResponse.CatalogValue rarity, String imageLargeUrl,
        String imageSmallUrl, CardResponse.AbilityValue ability, boolean owned, int copies) {

    /** Reutiliza la traducción de la ficha sin cambiar las respuestas administrativas. */
    public static CatalogCardResponse from(CatalogCardDetails details) {
        CardResponse card = CardResponse.from(details.card());
        return new CatalogCardResponse(card.code(), card.name(), card.cost(), card.power(), card.type(),
                card.rarity(), card.imageLargeUrl(), card.imageSmallUrl(), card.ability(),
                details.owned(), details.copies());
    }
}
