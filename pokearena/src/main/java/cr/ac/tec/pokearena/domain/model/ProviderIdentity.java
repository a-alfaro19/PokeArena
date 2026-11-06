package cr.ac.tec.pokearena.domain.model;

import java.util.Objects;

/**
 * Propósito: identidad de un jugador en un proveedor externo (sección 6, tabla player_identity).
 * Responsabilidades: representar el par (proveedor, identificador del proveedor) que identifica a
 * un jugador. Nunca es el correo, porque el correo puede cambiar en el proveedor.
 * Referencia: sección 6, "Reglas del modelo".
 *
 * @param provider nombre del proveedor, por ejemplo {@code google} o {@code dev}
 * @param subject  identificador del jugador en ese proveedor (claim {@code sub})
 */
public record ProviderIdentity(String provider, String subject) {

    /** Proveedor de las credenciales de prueba de {@code POST /api/v1/dev/sesiones} (sección 5.2). */
    public static final String DEV_PROVIDER = "dev";

    public ProviderIdentity {
        Objects.requireNonNull(provider, "El proveedor es obligatorio");
        Objects.requireNonNull(subject, "El identificador del proveedor es obligatorio");
        if (provider.isBlank() || subject.isBlank()) {
            throw new IllegalArgumentException("El proveedor y su identificador no pueden estar vacíos");
        }
    }

    /** Forma de texto usada en la configuración {@code app.admin.identities}: {@code proveedor:sub}. */
    @Override
    public String toString() {
        return provider + ":" + subject;
    }
}
