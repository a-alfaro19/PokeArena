package cr.ac.tec.pokearena.application.dto;

import java.util.List;

import cr.ac.tec.pokearena.domain.model.CardType;
import cr.ac.tec.pokearena.domain.model.Rarity;

/**
 * Propósito: opciones de los filtros del catálogo del jugador y de la búsqueda del administrador.
 * Responsabilidades: reunir los valores que la interfaz necesita antes de filtrar: tipos, rarezas, rango
 * de costo y límites de paginación, tomados de las mismas fuentes que validan las consultas.
 * Referencia: secciones 5.5, 7.3 y 8.3; {@code GET /api/v1/opciones/catalogo}.
 *
 * @param types      tipos de carta, ordenados por código
 * @param rarities   rarezas, ordenadas por código
 * @param cost       rango de costo
 * @param pagination límites de paginación
 */
public record CatalogOptions(List<CardType> types, List<Rarity> rarities, ValueRange cost,
        PaginationLimits pagination) {

    public CatalogOptions {
        types = List.copyOf(types);
        rarities = List.copyOf(rarities);
    }

    /**
     * Límites de {@code limit} (sección 5.5).
     *
     * @param defaultLimit tamaño sin {@code limit}
     * @param minLimit     tamaño mínimo
     * @param maxLimit     tamaño máximo
     */
    public record PaginationLimits(int defaultLimit, int minLimit, int maxLimit) {
    }
}
