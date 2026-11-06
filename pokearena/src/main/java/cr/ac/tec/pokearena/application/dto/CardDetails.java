package cr.ac.tec.pokearena.application.dto;

import cr.ac.tec.pokearena.domain.model.Card;

/**
 * Propósito: ficha de una carta, resultado de "Crear carta" (sección 7.1, "Ficha de respuesta").
 * Responsabilidades: reunir lo que ve el administrador: código, nombre, costo, poder, tipo, rareza,
 * imágenes, estado de imagen, disponibilidad y la habilidad con su nombre, su código y el tipo al que
 * está limitada. Es también lo que se guarda con la clave de idempotencia para repetir la respuesta.
 * Referencia: sección 7.1 y criterio C.1 ("la ficha muestra su tipo, su habilidad y ...").
 *
 * @param code          código, por ejemplo {@code PKA-FUE-001}
 * @param name          nombre
 * @param cost          costo
 * @param power         poder
 * @param type          tipo (código y nombre)
 * @param rarity        rareza (código y nombre)
 * @param imageLargeUrl URL de la versión grande
 * @param imageSmallUrl URL de la miniatura, o {@code null} si está pendiente
 * @param imageStatus   {@code READY}, {@code PENDING_RETRY} o {@code FAILED}
 * @param available     si los jugadores pueden verla
 * @param ability       habilidad asignada
 */
public record CardDetails(String code, String name, int cost, int power, CatalogValue type, CatalogValue rarity,
        String imageLargeUrl, String imageSmallUrl, String imageStatus, boolean available, AbilitySummary ability) {

    /** Valor de un catálogo: código y nombre. */
    public record CatalogValue(String code, String name) {
    }

    /**
     * Habilidad de la ficha.
     *
     * @param limitedToType código del tipo al que está limitada, o {@code null} si no lo está
     */
    public record AbilitySummary(String code, String name, String limitedToType) {
    }

    public static CardDetails of(Card card) {
        return new CardDetails(card.code().value(), card.fields().name(), card.fields().cost(), card.fields().power(),
                new CatalogValue(card.fields().type().code(), card.fields().type().name()),
                new CatalogValue(card.fields().rarity().code(), card.fields().rarity().name()),
                card.imageLargeUrl(), card.imageSmallUrl(), card.imageStatus().name(), card.available(),
                new AbilitySummary(card.ability().code(), card.ability().name(), card.ability().limitedToType()));
    }
}
