package cr.ac.tec.pokearena.application.port;

import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.Page;

/**
 * Propósito: puerto de la caché de páginas del catálogo y de la búsqueda (sección 9, cache-aside).
 * Responsabilidades: obtener y guardar una página por su clave, e invalidar todas las páginas cuando
 * cambia una carta. Las páginas no llevan datos del jugador: nunca se cachea la propiedad de las cartas.
 * Referencia: sección 9 ("Política de caché") y Anexo A.3 (mensajes 8 a 12).
 *
 * <p>Contrato que debe cumplir toda implementación:
 * <ul>
 *   <li>Una falla de la caché nunca es un error: {@link #get} devuelve vacío, {@link #put} e
 *       {@link #invalidate} no hacen nada, y el caso de uso consulta la base (sección 9, "Falla de Redis").</li>
 *   <li>La versión del catálogo ({@code catalog:version}) y el vencimiento ({@code app.cache.catalog-ttl})
 *       son asunto de la implementación: el caso de uso solo arma la parte de la clave que conoce.</li>
 * </ul>
 * Implementaciones: {@code RedisCatalogCache} (con Redis configurado) y {@code NoCatalogCache} (sin Redis).
 */
public interface CatalogCache {

    /** Página guardada con esa clave, si existe y no venció. */
    <T> Optional<Page<T>> get(CatalogCacheKey key, Class<T> itemType);

    /** Guarda la página con esa clave, con el vencimiento configurado. */
    void put(CatalogCacheKey key, Page<?> page);

    /**
     * Invalida todas las páginas del catálogo y de la búsqueda: incrementa {@code catalog:version}, así las
     * claves viejas dejan de usarse y vencen solas. Se llama DESPUÉS de confirmar la transacción que cambió
     * una carta (sección 9, "Invalidación").
     */
    void invalidate();
}
