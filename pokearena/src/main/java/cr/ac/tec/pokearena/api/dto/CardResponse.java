package cr.ac.tec.pokearena.api.dto;

import java.util.Map;

import cr.ac.tec.pokearena.application.dto.CardDetails;

/**
 * Propósito: cuerpo HTTP de la ficha de una carta (respuesta 201 de {@code POST /api/v1/cartas} y cada
 * elemento de {@code GET /api/v1/cartas}).
 * Responsabilidades: definir la forma JSON del contrato, separada de la ficha de la aplicación.
 * Referencia: sección 7.1 ("Ficha de respuesta") y sección 7.3.
 *
 * @param code          código, por ejemplo {@code PKA-FUE-001}
 * @param name          nombre
 * @param cost          costo
 * @param power         poder
 * @param type          tipo
 * @param rarity        rareza
 * @param imageLargeUrl URL de la versión grande
 * @param imageSmallUrl URL de la miniatura, o {@code null} si está pendiente
 * @param imageStatus   {@code READY}, {@code PENDING_RETRY} o {@code FAILED}
 * @param available     si los jugadores pueden verla
 * @param ability       habilidad
 */
public record CardResponse(String code, String name, int cost, int power, CatalogValue type, CatalogValue rarity,
        String imageLargeUrl, String imageSmallUrl, String imageStatus, boolean available, AbilityValue ability) {

    /** Valor de catálogo: código y nombre. */
    public record CatalogValue(String code, String name) {
    }

    /**
     * Habilidad: código, nombre, tipo al que está limitada ({@code null} si no lo está), disparador y
     * efecto ({@code null} si no tiene) y parámetros (vacío si no tiene).
     */
    public record AbilityValue(String code, String name, String limitedToType, String trigger, String effect,
            Map<String, Object> parameters) {
    }

    public static CardResponse from(CardDetails details) {
        return new CardResponse(details.code(), details.name(), details.cost(), details.power(),
                new CatalogValue(details.type().code(), details.type().name()),
                new CatalogValue(details.rarity().code(), details.rarity().name()),
                details.imageLargeUrl(), details.imageSmallUrl(), details.imageStatus(), details.available(),
                new AbilityValue(details.ability().code(), details.ability().name(), details.ability().limitedToType(),
                        details.ability().trigger(), details.ability().effect(), details.ability().parameters()));
    }
}
