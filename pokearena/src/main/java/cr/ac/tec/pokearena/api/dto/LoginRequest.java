package cr.ac.tec.pokearena.api.dto;

/**
 * Propósito: cuerpo de {@code POST /api/v1/sesiones} (sección 8.1).
 * Responsabilidades: recibir el proveedor y su token como texto, sin validarlos: un campo ausente lo
 * informa el caso de uso como MISSING_FIELD, con el mismo formato que las demás historias.
 * Referencia: sección 8.1, paso 1.
 *
 * @param provider nombre del proveedor, por ejemplo {@code google}
 * @param idToken  token de identidad que el proveedor entregó a la interfaz
 */
public record LoginRequest(String provider, String idToken) {

    /** El token nunca aparece en logs. */
    @Override
    public String toString() {
        return "LoginRequest[provider=" + provider + "]";
    }
}
