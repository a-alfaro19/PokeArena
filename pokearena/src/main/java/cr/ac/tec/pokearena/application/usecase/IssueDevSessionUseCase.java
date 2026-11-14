package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.dto.DevSessionResult;
import cr.ac.tec.pokearena.application.dto.Session;
import cr.ac.tec.pokearena.application.port.AuthenticatedUser;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;

/**
 * Propósito: emitir credenciales de prueba para ADMIN o PLAYER (sección 5.2, solo perfil dev).
 * Responsabilidades: obtener un jugador REAL con proveedor {@code dev}, por la misma vía que el
 * inicio de sesión ({@link PlayerAccountService}), entregar la colección inicial al jugador de prueba
 * de rol PLAYER (también por la misma vía, sin impedir la sesión si falla), emitir sus credenciales e
 * informar el estado de la colección con el mismo significado que el inicio de sesión (sección 8.1, paso 8).
 * Referencia: secciones 5.2 y 8.1 (paso 6).
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
    private final NonBlockingInitialCollection initialCollection;
    private final TokenIssuer tokenIssuer;

    public IssueDevSessionUseCase(PlayerAccountService accounts, InitialCollectionDelivery initialCollection,
            TokenIssuer tokenIssuer) {
        this.accounts = accounts;
        this.initialCollection = new NonBlockingInitialCollection(initialCollection);
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * Abre una sesión de prueba.
     *
     * @return la sesión y el estado de la colección inicial; el estado es {@code null} para el
     *         administrador de prueba, que no recibe colección
     */
    public DevSessionResult execute(Role requestedRole) {
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

        // El jugador de prueba recibe la colección inicial por la misma vía que el inicio de sesión, para
        // que las demostraciones del catálogo tengan cartas propias. Su resultado viaja en la respuesta,
        // igual que en el inicio de sesión (así la interfaz puede confirmar ALREADY_DELIVERED), y una falla
        // no impide abrir la sesión de prueba (PENDING_ERROR). El administrador de prueba no la recibe.
        InitialCollectionStatus collection = admin ? null : initialCollection.deliver(player.id());

        Session session = new Session(player, tokenIssuer.issue(new AuthenticatedUser(player.id(), player.role())));
        return new DevSessionResult(session, collection);
    }
}
