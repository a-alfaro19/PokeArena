package cr.ac.tec.pokearena.api.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Propósito: cuerpo JSON único de todas las respuestas de error (sección 5.3, RNF-API-03).
 * Responsabilidades: transportar el tipo, el título, el status, el detalle para el usuario y,
 * cuando aplican, el campo y los valores permitidos, siempre con el {@code correlationId}.
 * Referencia: sección 5.3.
 *
 * <p>Los campos opcionales ({@code field}, {@code allowedValues}) no aparecen en el JSON cuando
 * no aplican, en lugar de salir como {@code null}.
 *
 * @param type          código del error, por ejemplo {@code INVALID_FIELD}
 * @param title         título corto en español, fijo por tipo
 * @param status        status HTTP, igual al de la respuesta
 * @param detail        explicación para el usuario, en español
 * @param field         campo afectado, si aplica
 * @param allowedValues valores permitidos del campo, si aplica
 * @param correlationId identificador de la solicitud (sección 5.6)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String field,
        List<String> allowedValues,
        String correlationId) {

    public ApiError {
        // Una lista vacía equivale a "no aplica": se omite del JSON como los demás opcionales.
        allowedValues = allowedValues == null || allowedValues.isEmpty() ? null : List.copyOf(allowedValues);
    }
}
