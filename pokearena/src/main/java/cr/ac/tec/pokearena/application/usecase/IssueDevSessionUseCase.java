package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.Session;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: emitir credenciales de prueba para ADMIN o PLAYER (sección 5.2, solo perfil dev).
 * Responsabilidades: obtener un jugador REAL con proveedor {@code dev}, por la misma vía que el
 * inicio de sesión ({@link PlayerAccountService}), y emitir sus credenciales propias.
 * Referencia: sección 5.2.
 *
 * <p>El rol del jugador de prueba no lo elige este caso de uso: sale de {@code app.admin.identities},
 * igual que para cualquier cuenta. El perfil dev declara {@code dev:dev-admin} como administrador.
 * Así se prueba la misma regla que usará el inicio de sesión con Google.
 */
public class IssueDevSessionUseCase {

    /** Identificador en el proveedor dev del administrador de prueba. */
    public static final String DEV_ADMIN_SUBJECT = "dev-admin";

    /** Identificador en el proveedor dev del jugador de prueba. */
    public static final String DEV_PLAYER_SUBJECT = "dev-player";

    private final PlayerAccountService accounts;
    private final TokenIssuer tokenIssuer;

    public IssueDevSessionUseCase(PlayerAccountService accounts, TokenIssuer tokenIssuer) {
        this.accounts = accounts;
        this.tokenIssuer = tokenIssuer;
    }

    public Session execute(Role requestedRole) {
        // Cada rol pedido corresponde a una identidad fija del proveedor dev: así dos llamadas con el
        // mismo rol reutilizan el mismo jugador en lugar de crear otro.
        boolean admin = requestedRole == Role.ADMIN;
        ProviderIdentity identity = new ProviderIdentity(ProviderIdentity.DEV_PROVIDER,
                admin ? DEV_ADMIN_SUBJECT : DEV_PLAYER_SUBJECT);
        Player player = accounts.findOrCreate(identity,
                admin ? "Administrador de prueba" : "Jugador de prueba", null);

        // Si la configuración no le da a la identidad el rol pedido, es un error de configuración:
        // se detiene aquí en lugar de entregar credenciales con un rol distinto al solicitado.
        if (player.role() != requestedRole) {
            throw new IllegalStateException("La identidad " + identity + " tiene el rol " + player.role()
                    + " y se pidió " + requestedRole + ". Revise app.admin.identities del perfil dev.");
        }

        return new Session(player, tokenIssuer.issue(new AuthenticatedUser(player.id(), player.role())));
    }
}
