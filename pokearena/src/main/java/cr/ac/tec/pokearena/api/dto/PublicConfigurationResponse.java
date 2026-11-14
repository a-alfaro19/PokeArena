package cr.ac.tec.pokearena.api.dto;

/**
 * Propósito: respuesta de {@code GET /api/v1/configuracion} (sección 11).
 * Responsabilidades: entregar a la interfaz el {@code client id} de Google, o {@code null} si no está
 * configurado (entonces la interfaz deja el botón de Google deshabilitado).
 * Referencia: sección 11 (pantalla de ingreso).
 *
 * @param googleClientId {@code client id} de PokéArena en Google, o {@code null}
 */
public record PublicConfigurationResponse(String googleClientId) {

    /** Un valor vacío o con solo espacios equivale a "no configurado". */
    public static PublicConfigurationResponse of(String googleClientId) {
        return new PublicConfigurationResponse(
                googleClientId == null || googleClientId.isBlank() ? null : googleClientId.trim());
    }
}
