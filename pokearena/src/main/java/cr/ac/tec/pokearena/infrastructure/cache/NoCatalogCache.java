package cr.ac.tec.pokearena.infrastructure.cache;

import java.util.Optional;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.dto.Page;
import cr.ac.tec.pokearena.application.port.CatalogCache;
import cr.ac.tec.pokearena.application.port.CatalogCacheKey;

/**
 * Propósito: implementación provisional de {@link CatalogCache} que no guarda nada.
 * Responsabilidades: cumplir el contrato del puerto sin Redis: {@link #get} siempre responde que la
 * página no está y {@link #put} no hace nada. Es exactamente el comportamiento que la sección 9 exige
 * cuando Redis falla ("se ignora la caché y se consulta la base"), así que la aplicación funciona
 * completa, solo que sin caché.
 * Referencia: sección 9 y Anexo A.3 (rama "la página no está en caché").
 *
 * <p>La implementación sobre Redis va en su propia tarea y reemplazará este bean.
 */
@Component
public class NoCatalogCache implements CatalogCache {

    @Override
    public <T> Optional<Page<T>> get(CatalogCacheKey key, Class<T> itemType) {
        return Optional.empty();
    }

    @Override
    public void put(CatalogCacheKey key, Page<?> page) {
        // Sin caché: no se guarda nada.
    }
}
