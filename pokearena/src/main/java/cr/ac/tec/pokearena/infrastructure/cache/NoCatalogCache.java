package cr.ac.tec.pokearena.infrastructure.cache;

import java.util.Optional;

import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;

/**
 * Propósito: implementación de {@link CatalogCache} para cuando no hay Redis configurado.
 * Responsabilidades: cumplir el contrato del puerto sin guardar nada: {@link #get} siempre responde que la
 * página no está, y {@link #put} e {@link #invalidate} no hacen nada. Es exactamente el comportamiento que
 * la sección 9 exige cuando Redis falla ("se ignora la caché y se consulta la base"), así que la aplicación
 * funciona completa, solo que sin caché.
 * Referencia: sección 9 y Anexo A.3 (rama "la página no está en caché").
 *
 * <p>La elige {@code CacheConfiguration} cuando {@code app.cache.redis-host} (variable
 * {@code REDIS_HOST}) está vacía: así las pruebas y el desarrollo local funcionan sin Redis.
 */
public class NoCatalogCache implements CatalogCache {

    @Override
    public <T> Optional<Page<T>> get(CatalogCacheKey key, Class<T> itemType) {
        return Optional.empty();
    }

    @Override
    public void put(CatalogCacheKey key, Page<?> page) {
        // Sin caché: no se guarda nada.
    }

    @Override
    public void invalidate() {
        // Sin caché: no hay páginas que invalidar.
    }
}
