package cr.ac.tec.pokearena.application.port;

import java.util.Objects;

import cr.ac.tec.pokearena.domain.model.ProviderIdentity;

/**
 * Propósito: identidad que un {@link IdentityProvider} ya verificó (sección 8.1, paso 4).
 * Responsabilidades: llevar al caso de uso el proveedor, el identificador del jugador en ese proveedor
 * ({@code sub}) y los datos de perfil que informó (correo y nombre, que pueden faltar).
 * Referencia: sección 8.1, paso 4, y sección 6 ("la identidad del jugador es (provider, provider_subject),
 * nunca el correo").
 *
 * @param provider    nombre del proveedor que verificó el token
 * @param subject     identificador del jugador en el proveedor (claim {@code sub})
 * @param email       correo informado por el proveedor, o {@code null}
 * @param displayName nombre informado por el proveedor, o {@code null}
 */
public record VerifiedIdentity(String provider, String subject, String email, String displayName) {

    public VerifiedIdentity {
        Objects.requireNonNull(provider, "El proveedor es obligatorio");
        Objects.requireNonNull(subject, "El identificador del proveedor es obligatorio");
    }

    /** Lo único que identifica la cuenta: el par (proveedor, sub). El correo y el nombre no cuentan. */
    public ProviderIdentity identity() {
        return new ProviderIdentity(provider, subject);
    }
}
