package cr.ac.tec.pokearena.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Propósito: efecto de una ubicación, ya validado por su plug-in (sección 7.2, paso 6).
 * Responsabilidades: llevar el código del tipo de efecto y sus parámetros hasta la ubicación. Solo lo
 * crea {@code LocationEffectRegistry}, después de que el plug-in del tipo aceptó los parámetros.
 * Referencia: sección 6 (columnas effect_type y effect_parameters) y criterio C.2.
 *
 * @param code       tipo de efecto, por ejemplo {@code TYPE_BONUS}
 * @param parameters parámetros, por ejemplo {@code {"type": "FUE", "amount": 3}}
 */
public record LocationEffect(String code, Map<String, Object> parameters) {

    public LocationEffect {
        Objects.requireNonNull(code, "El código del efecto es obligatorio");
        // Copia que conserva el orden y no se puede modificar desde afuera.
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters == null ? Map.of() : parameters));
    }
}
