package cr.ac.tec.pokearena.infrastructure.persistence;

import org.springframework.stereotype.Component;

import cr.ac.tec.pokearena.application.port.ResponseSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Propósito: implementación JSON de {@link ResponseSerializer} con Jackson (sección 5.4).
 * Responsabilidades: convertir el resultado de un caso de uso en el JSON de la columna
 * response_body y reconstruirlo al repetir una respuesta.
 * Referencia: sección 5.4.
 *
 * <p>Usa el {@code JsonMapper} que configura Spring Boot, el mismo que escribe las respuestas
 * HTTP: así el resultado guardado se reconstruye con las mismas reglas con que se envió.
 */
@Component
public class JacksonResponseSerializer implements ResponseSerializer {

    private final JsonMapper jsonMapper;

    public JacksonResponseSerializer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String serialize(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    @Override
    public <T> T deserialize(String text, Class<T> type) {
        return jsonMapper.readValue(text, type);
    }
}
