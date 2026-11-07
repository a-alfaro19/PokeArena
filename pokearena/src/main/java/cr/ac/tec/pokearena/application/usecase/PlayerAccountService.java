package cr.ac.tec.pokearena.application.usecase;

import cr.ac.tec.pokearena.application.port.Clock;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.NewPlayer;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.PlayerAccount;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.AdminIdentities;

/**
 * Propósito: obtener la cuenta de un jugador a partir de su identidad del proveedor, creándola si
 * no existe. Es el paso 5 del inicio de sesión (Anexo A.4), compartido por {@code dev/sesiones}.
 * Responsabilidades: decidir el rol de la cuenta en cada ingreso ({@link AdminIdentities}),
 * actualizarlo si la configuración cambió, tomar la fecha de creación del puerto {@link Clock} y
 * ejecutar todo en una transacción.
 * Referencia: sección 8.1 (paso 5) y sección 5.2 (las credenciales de prueba usan la misma vía).
 *
 * <p>No es un caso de uso de una historia: es la parte común que usarán el inicio de sesión con
 * Google (1.1.1) y las credenciales de prueba, para que ambos creen jugadores exactamente igual.
 */
public class PlayerAccountService {

    private final UnitOfWork unitOfWork;
    private final PlayerRepository players;
    private final AdminIdentities adminIdentities;
    private final Clock clock;

    public PlayerAccountService(UnitOfWork unitOfWork, PlayerRepository players, AdminIdentities adminIdentities,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.players = players;
        this.adminIdentities = adminIdentities;
        this.clock = clock;
    }

    /**
     * Devuelve el jugador de {@code identity}, creándolo si es su primer ingreso.
     *
     * @param displayName nombre que informó el proveedor (puede ser nulo)
     * @param email       correo que informó el proveedor (puede ser nulo)
     */
    public Player findOrCreate(ProviderIdentity identity, String displayName, String email) {
        return findOrCreateAccount(identity, displayName, email).player();
    }

    /**
     * Igual que {@link #findOrCreate}, pero dice además si esta llamada creó la cuenta. Lo usa el
     * inicio de sesión para informar {@code firstLogin} (sección 8.1, paso 8).
     */
    public PlayerAccount findOrCreateAccount(ProviderIdentity identity, String displayName, String email) {
        // El rol se calcula en cada ingreso: app.admin.identities es la fuente de verdad (sección 5.2).
        Role configuredRole = adminIdentities.roleFor(identity);
        NewPlayer candidate = new NewPlayer(displayName, email, configuredRole, clock.now());

        return unitOfWork.execute(() -> {
            // Paso 5 del diagrama A.4: buscar el jugador por proveedor y sub, o crearlo si no existe.
            PlayerAccount account = players.findOrCreate(identity, candidate);
            Player player = account.player();

            // Una cuenta existente toma el rol de la configuración actual: quien sale de la lista
            // pierde ADMIN y quien entra lo recibe, sin borrar su cuenta. Solo se escribe si cambió.
            if (player.role() == configuredRole) {
                return account;
            }
            players.updateRole(player.id(), configuredRole);
            return new PlayerAccount(new Player(player.id(), player.displayName(), player.email(), configuredRole),
                    account.created());
        });
    }
}
