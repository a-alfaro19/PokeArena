package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.CatalogOptions;
import cr.ac.tec.pokearena.application.dto.ValueRange;
import cr.ac.tec.pokearena.domain.repository.CardCatalogRepository;
import cr.ac.tec.pokearena.domain.rule.CardFieldRules;

/**
 * Propósito: opciones de los filtros del catálogo del jugador (3.1.2) y de la búsqueda del administrador
 * (2.1.2), que la interfaz pide antes de mostrarlos.
 * Responsabilidades: leer tipos y rarezas de sus repositorios y tomar el rango de costo y los límites de
 * paginación de las mismas clases que validan las consultas.
 * Referencia: Anexos A.3 y A.6 (la interfaz pide las opciones antes del filtro); secciones 5.5, 7.3 y 8.3.
 *
 * <h2>Misma plantilla que los demás casos de uso (corrección 7)</h2>
 * <ol>
 *   <li><b>Autorizar:</b> ya ocurrió en la API ({@code @RequiresRole({PLAYER, ADMIN})} en
 *       {@code OptionsController}): las usan el jugador y el administrador.</li>
 *   <li><b>Idempotencia:</b> no aplica; es una lectura (GET).</li>
 *   <li><b>Validar:</b> no hay parámetros que validar.</li>
 *   <li><b>Ejecutar:</b> dos lecturas de catálogos fijos, sin {@code UnitOfWork}: no hay nada que confirmar
 *       ni revertir.</li>
 *   <li><b>Responder:</b> 200 con las opciones.</li>
 * </ol>
 *
 * <h2>Decisiones</h2>
 * <ul>
 *   <li>Una sola fuente: tipos y rarezas son los mismos que reciben {@code CardSearchRules} y
 *       {@code CatalogRules}; el costo, el de {@link CardFieldRules}; la paginación, la de
 *       {@link Pagination}. Si cambia una regla, la interfaz la ve sin tocar su código.</li>
 *   <li>El orden es por código, el mismo de los {@code allowedValues} de los errores: estable entre
 *       solicitudes.</li>
 * </ul>
 */
public class GetCatalogOptionsUseCase {

    private final CardCatalogRepository catalog;
    private final Pagination pagination;

    public GetCatalogOptionsUseCase(CardCatalogRepository catalog, Pagination pagination) {
        this.catalog = catalog;
        this.pagination = pagination;
    }

    /** Opciones de los filtros. */
    public CatalogOptions execute() {
        // La interfaz pide las opciones antes del filtro (Anexos A.3 y A.6); la seguridad ya validó la sesión.
        return new CatalogOptions(
                // Tipos y rarezas desde sus repositorios (tablas card_type y rarity).
                catalog.findAllTypes(),
                catalog.findAllRarities(),
                new ValueRange(CardFieldRules.MIN_COST, CardFieldRules.MAX_COST),
                new CatalogOptions.PaginationLimits(pagination.defaultLimit(), Pagination.MIN_LIMIT,
                        pagination.maxLimit()));
    }
}
