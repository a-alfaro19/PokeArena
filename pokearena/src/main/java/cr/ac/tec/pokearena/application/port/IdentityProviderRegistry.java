package cr.ac.tec.pokearena.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cr.ac.tec.pokearena.domain.error.IdentityProviderException;

/**
 * Propósito: catálogo de proveedores de identidad (patrón Registry con Strategy, sección 4.4;
 * participante "Registro de proveedores" del diagrama A.4).
 * Responsabilidades: encontrar el {@link IdentityProvider} por su nombre, o rechazar con
 * UNSUPPORTED_PROVIDER un nombre que no está registrado o cuyo proveedor está deshabilitado.
 * Referencia: sección 8.1, paso 2, y sección 4.4 ("agregar un proveedor sin modificar el caso de uso").
 *
 * <p>Recibe la lista de proveedores desde afuera (en la aplicación, todos los beans que implementan
 * {@link IdentityProvider}): agregar uno es agregar una clase, sin modificar este registro, el caso de
 * uso ni la autorización (abierto/cerrado). Es la misma forma que {@code LocationEffectRegistry}.
 *
 * <p>Vive con los puertos porque es la puerta de entrada a ellos, y no depende de ninguna implementación.
 */
public final class IdentityProviderRegistry {

    // Ordenado por nombre: los valores permitidos del error salen siempre en el mismo orden.
    private final Map<String, IdentityProvider> providers = new TreeMap<>();

    /**
     * @param candidates todos los proveedores conocidos; los deshabilitados no se registran
     * @throws IllegalArgumentException si dos proveedores habilitados usan el mismo nombre
     */
    public IdentityProviderRegistry(Collection<? extends IdentityProvider> candidates) {
        for (IdentityProvider provider : candidates) {
            if (!provider.isEnabled()) {
                continue;
            }
            if (providers.putIfAbsent(provider.name(), provider) != null) {
                throw new IllegalArgumentException("Hay dos proveedores de identidad con el nombre " + provider.name());
            }
        }
    }

    /** Nombres de los proveedores disponibles, ordenados. */
    public List<String> names() {
        return List.copyOf(providers.keySet());
    }

    /**
     * Paso 2 del diagrama A.4: obtener el proveedor.
     *
     * @throws IdentityProviderException UNSUPPORTED_PROVIDER si no hay un proveedor disponible con ese nombre
     */
    public IdentityProvider get(String name) {
        IdentityProvider provider = providers.get(name);
        if (provider == null) {
            throw IdentityProviderException.unsupported(
                    "El proveedor de identidad '" + name + "' no está disponible.", names());
        }
        return provider;
    }
}
