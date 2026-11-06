package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.port.EffectReader;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.domain.rule.LocationFieldRules;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: implementación JSON de {@link EffectReader} con Jackson.
 * Responsabilidades: convertir el texto {@code {"code": "...", "parameters": {...}}} en el código y los
 * parámetros del efecto. Un texto que no tiene esa forma es INVALID_EFFECT sobre {@code effect}, como
 * cualquier otro efecto inválido.
 * Referencia: sección 7.2, paso 6.
 *
 * <p>Está junto a {@link JacksonResponseSerializer}: ambos traducen JSON para la aplicación con el
 * mismo {@code JsonMapper} de Spring Boot.
 */
@Component
public class JacksonEffectReader implements EffectReader {

    private static final String CODE = "code";
    private static final String PARAMETERS = "parameters";
    private static final Set<String> KEYS = Set.of(CODE, PARAMETERS);
    private static final String EXPECTED_FORM = "El efecto debe ser un JSON {\"code\": \"...\", \"parameters\": {...}}.";

    private final JsonMapper json;

    public JacksonEffectReader(JsonMapper json) {
        this.json = json;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<EffectInput> read(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Object parsed;
        try {
            parsed = json.readValue(text, Object.class);
        } catch (JacksonException malformed) {
            throw invalid(EXPECTED_FORM);
        }
        // Solo se aceptan las claves "code" y "parameters": cualquier otra es un error de escritura.
        if (!(parsed instanceof Map<?, ?> object) || !KEYS.containsAll(object.keySet())) {
            throw invalid(EXPECTED_FORM);
        }
        if (!(object.get(CODE) instanceof String code) || code.isBlank()) {
            throw invalid("Falta el código del efecto (\"code\"). " + EXPECTED_FORM);
        }
        Object parameters = object.get(PARAMETERS);
        if (parameters != null && !(parameters instanceof Map<?, ?>)) {
            throw invalid("Los parámetros del efecto (\"parameters\") deben ser un objeto JSON.");
        }
        return Optional.of(new EffectInput(code,
                parameters == null ? Map.of() : new LinkedHashMap<>((Map<String, Object>) parameters)));
    }

    private static ValidationException invalid(String message) {
        return ValidationException.invalidEffect(LocationFieldRules.EFFECT_FIELD, message, List.of());
    }
}
