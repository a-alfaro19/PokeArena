package cr.ac.tec.pokearena.api.dto;

import java.util.List;

import cr.ac.tec.pokearena.api.dto.CardResponse.CatalogValue;
import cr.ac.tec.pokearena.application.dto.CatalogOptions;

/**
 * Propósito: cuerpo HTTP de {@code GET /api/v1/opciones/catalogo} (esquema {@code CatalogOptions}).
 * Responsabilidades: definir la forma JSON del contrato, separada de las opciones de la aplicación.
 * Referencia: secciones 5.5, 7.3 y 8.3.
 *
 * @param types      tipos de carta, ordenados por código
 * @param rarities   rarezas, ordenadas por código
 * @param cost       rango de costo
 * @param pagination límites de paginación
 */
public record CatalogOptionsResponse(List<CatalogValue> types, List<CatalogValue> rarities, RangeValue cost,
        PaginationValue pagination) {

    /** Rango de enteros, ambos extremos inclusivos (esquema {@code Range}). */
    public record RangeValue(int min, int max) {
    }

    /** Límites de {@code limit} (sección 5.5). */
    public record PaginationValue(int defaultLimit, int minLimit, int maxLimit) {
    }

    public static CatalogOptionsResponse from(CatalogOptions options) {
        return new CatalogOptionsResponse(
                options.types().stream().map(type -> new CatalogValue(type.code(), type.name())).toList(),
                options.rarities().stream().map(rarity -> new CatalogValue(rarity.code(), rarity.name())).toList(),
                new RangeValue(options.cost().min(), options.cost().max()),
                new PaginationValue(options.pagination().defaultLimit(), options.pagination().minLimit(),
                        options.pagination().maxLimit()));
    }
}
