package cr.ac.tec.pokearena.domain.rule;

import java.util.Set;

import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: regla que decide el rol de una cuenta nueva (sección 5.2).
 * Responsabilidades: dar el rol ADMIN a las identidades configuradas como administradoras y
 * PLAYER a todas las demás. La lista viene de la propiedad {@code app.admin.identities}; esta
 * clase no sabe de dónde sale, solo aplica la regla.
 * Referencia: sección 5.2 ("El rol de administrador se asigna por configuración").
 */
public final class AdminIdentities {

    private final Set<ProviderIdentity> administrators;

    public AdminIdentities(Set<ProviderIdentity> administrators) {
        this.administrators = Set.copyOf(administrators);
    }

    /** Rol que corresponde a una identidad al crear su cuenta. */
    public Role roleFor(ProviderIdentity identity) {
        return administrators.contains(identity) ? Role.ADMIN : Role.PLAYER;
    }
}
