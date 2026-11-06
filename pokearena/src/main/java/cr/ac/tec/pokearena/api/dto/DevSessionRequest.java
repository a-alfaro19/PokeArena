package cr.ac.tec.pokearena.api.dto;

/**
 * Propósito: cuerpo de {@code POST /api/v1/dev/sesiones} (sección 5.2).
 * Responsabilidades: recibir el rol pedido como texto, para que un valor ausente o desconocido se
 * informe como MISSING_FIELD o INVALID_FIELD con los valores permitidos, y no como un cuerpo ilegible.
 * Referencia: sección 5.2.
 *
 * @param role {@code "ADMIN"} o {@code "PLAYER"}
 */
public record DevSessionRequest(String role) {

    /** Nombre del campo, para los errores. */
    public static final String ROLE_FIELD = "role";
}
