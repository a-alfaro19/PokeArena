package cr.ac.tec.pokearena.domain.effect;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.model.LocationEffect;

/**
 * Propósito: catálogo de tipos de efecto de ubicación (patrón Registry, sección 4.4; participante
 * "Registro de efectos de ubicación" del diagrama A.2).
 * Responsabilidades: encontrar el plug-in de un código y pedirle que valide los parámetros. Recibe la
 * lista de plug-ins desde afuera: agregar un efecto es agregar una clase a esa lista, sin modificar
 * este registro ni el caso de uso (abierto/cerrado).
 * Referencia: sección 7.2, paso 6, y criterio C.2.
 */
public final class LocationEffectRegistry {

    // Ordenado por código: los valores permitidos de los errores salen siempre en el mismo orden.
    private final Map<String, LocationEffectType> types = new TreeMap<>();

    public LocationEffectRegistry(Collection<? extends LocationEffectType> effectTypes) {
        for (LocationEffectType type : effectTypes) {
            if (types.putIfAbsent(type.code(), type) != null) {
                throw new IllegalArgumentException("Hay dos plug-ins con el código de efecto " + type.code());
            }
        }
    }

    /** Códigos de los efectos registrados, ordenados. */
    public List<String> codes() {
        return List.copyOf(types.keySet());
    }

    /**
     * Paso 6: valida el efecto. El efecto es opcional.
     *
     * @param code       código del tipo de efecto, o {@code null} o vacío si la ubicación no tiene efecto
     * @param parameters parámetros del efecto ({@code null} equivale a ninguno)
     * @return el efecto validado, o vacío si la ubicación no tiene efecto
     * @throws ValidationException INVALID_EFFECT si el tipo no existe o los parámetros no son válidos
     */
    public Optional<LocationEffect> validate(String code, Map<String, Object> parameters) {
        Map<String, Object> safeParameters = parameters == null ? Map.of() : parameters;
        if (code == null || code.isBlank()) {
            // Sin efecto no hay parámetros: unos parámetros sueltos indican un error del formulario.
            if (!safeParameters.isEmpty()) {
                throw EffectParameters.invalid("Hay parámetros de efecto pero no se indicó el tipo de efecto.",
                        codes());
            }
            return Optional.empty();
        }
        LocationEffectType type = types.get(code);
        if (type == null) {
            throw EffectParameters.invalid("El tipo de efecto '" + code + "' no existe.", codes());
        }
        type.validate(safeParameters);
        return Optional.of(new LocationEffect(code, safeParameters));
    }
}
