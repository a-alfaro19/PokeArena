package cr.ac.tec.pokearena.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cr.ac.tec.pokearena.application.dto.DevSessionResult;
import cr.ac.tec.pokearena.application.dto.Session;
import cr.ac.tec.pokearena.application.port.InitialCollectionDelivery;
import cr.ac.tec.pokearena.application.port.InitialCollectionStatus;
import cr.ac.tec.pokearena.application.port.TokenIssuer;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.rule.AdminIdentities;
import cr.ac.tec.pokearena.infrastructure.config.AccountConfiguration;
import cr.ac.tec.pokearena.support.InMemoryPlayerRepository;

/**
 * Propósito: pruebas unitarias de la creación de cuentas y de las credenciales de prueba (sección 5.2).
 * Responsabilidades: comprobar que el rol de una cuenta nueva sale de app.admin.identities, que una
 * cuenta existente se reutiliza con su rol, que la fecha viene del reloj, y que dev/sesiones usa la
 * misma vía. Sin Spring ni base de datos: un repositorio en memoria y un reloj fijo.
 * Referencia: secciones 5.2 y 8.1 (paso 5).
 */
@DisplayName("Cuentas de jugadores y credenciales de prueba")
class PlayerAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final ProviderIdentity CONFIGURED_ADMIN = new ProviderIdentity("google", "sub-admin");
    private static final ProviderIdentity ORDINARY = new ProviderIdentity("google", "sub-jugador");

    private final InMemoryPlayerRepository players = new InMemoryPlayerRepository();
    private final UnitOfWork directUnitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private PlayerAccountService service(Set<ProviderIdentity> admins) {
        return new PlayerAccountService(directUnitOfWork, players, new AdminIdentities(admins), () -> NOW);
    }

    @Test
    @DisplayName("Una identidad configurada en app.admin.identities recibe el rol ADMIN")
    void configuredIdentityBecomesAdmin() {
        Player player = service(Set.of(CONFIGURED_ADMIN)).findOrCreate(CONFIGURED_ADMIN, "Ana", null);

        assertThat(player.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Una identidad que no está configurada recibe el rol PLAYER")
    void otherIdentityBecomesPlayer() {
        Player player = service(Set.of(CONFIGURED_ADMIN)).findOrCreate(ORDINARY, "Luis", null);

        assertThat(player.role()).isEqualTo(Role.PLAYER);
    }

    @Test
    @DisplayName("La misma identidad reutiliza su cuenta: nunca se crea una segunda")
    void existingAccountIsReused() {
        Player first = service(Set.of()).findOrCreate(ORDINARY, "Luis", null);
        Player second = service(Set.of()).findOrCreate(ORDINARY, "Luis", null);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(players.created()).hasSize(1);
    }

    @Test
    @DisplayName("Una cuenta PLAYER pasa a ADMIN en su siguiente ingreso al agregarla a la configuración")
    void playerBecomesAdminWhenAddedToConfiguration() {
        Player before = service(Set.of()).findOrCreate(ORDINARY, "Luis", null);

        Player after = service(Set.of(ORDINARY)).findOrCreate(ORDINARY, "Luis", null);

        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.role()).isEqualTo(Role.ADMIN);
        assertThat(players.findByIdentity(ORDINARY)).get().extracting(Player::role).isEqualTo(Role.ADMIN);
        assertThat(players.roleUpdates()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cuenta ADMIN vuelve a PLAYER en su siguiente ingreso al quitarla de la configuración")
    void adminBecomesPlayerWhenRemovedFromConfiguration() {
        Player before = service(Set.of(CONFIGURED_ADMIN)).findOrCreate(CONFIGURED_ADMIN, "Ana", null);
        assertThat(before.role()).isEqualTo(Role.ADMIN);

        Player after = service(Set.of()).findOrCreate(CONFIGURED_ADMIN, "Ana", null);

        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.role()).isEqualTo(Role.PLAYER);
        assertThat(players.roleUpdates()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si el rol no cambió, el ingreso no escribe el rol")
    void unchangedRoleIsNotWritten() {
        service(Set.of(CONFIGURED_ADMIN)).findOrCreate(CONFIGURED_ADMIN, "Ana", null);
        service(Set.of(CONFIGURED_ADMIN)).findOrCreate(CONFIGURED_ADMIN, "Ana", null);
        service(Set.of()).findOrCreate(ORDINARY, "Luis", null);
        service(Set.of()).findOrCreate(ORDINARY, "Luis", null);

        assertThat(players.roleUpdates()).isZero();
    }

    @Test
    @DisplayName("La fecha de creación de la cuenta viene del puerto Clock")
    void createdAtComesFromClock() {
        service(Set.of()).findOrCreate(ORDINARY, "Luis", null);

        assertThat(players.created().get(ORDINARY).createdAt()).isEqualTo(NOW);
    }

    @Nested
    @DisplayName("Lectura de app.admin.identities")
    class AdminIdentitiesConfiguration {

        private final AccountConfiguration configuration = new AccountConfiguration();

        @Test
        @DisplayName("Lee entradas proveedor:sub e ignora las vacías")
        void parsesEntries() {
            AdminIdentities admins = configuration.adminIdentities(List.of(" google:sub-admin ", "", "dev:dev-admin"),
                    List.of());

            assertThat(admins.roleFor(CONFIGURED_ADMIN)).isEqualTo(Role.ADMIN);
            assertThat(admins.roleFor(new ProviderIdentity("dev", "dev-admin"))).isEqualTo(Role.ADMIN);
            assertThat(admins.roleFor(ORDINARY)).isEqualTo(Role.PLAYER);
        }

        @Test
        @DisplayName("El administrador de prueba del perfil dev se suma a la lista, aunque APP_ADMIN_IDENTITIES la deje vacía")
        void devIdentitiesAreAdded() {
            AdminIdentities admins = configuration.adminIdentities(List.of(), List.of("dev:dev-admin"));
            AdminIdentities both = configuration.adminIdentities(List.of("google:sub-admin"), List.of("dev:dev-admin"));

            assertThat(admins.roleFor(new ProviderIdentity("dev", "dev-admin"))).isEqualTo(Role.ADMIN);
            assertThat(both.roleFor(CONFIGURED_ADMIN)).isEqualTo(Role.ADMIN);
            assertThat(both.roleFor(new ProviderIdentity("dev", "dev-admin"))).isEqualTo(Role.ADMIN);
            assertThatThrownBy(() -> configuration.adminIdentities(List.of(), List.of("mal")))
                    .hasMessageContaining("app.admin.dev-identities");
        }

        @Test
        @DisplayName("Una entrada sin el formato proveedor:sub detiene el arranque con un mensaje claro")
        void rejectsMalformedEntry() {
            assertThatThrownBy(() -> configuration.adminIdentities(List.of("solo-un-valor"), List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.admin.identities")
                    .hasMessageContaining("proveedor:sub");
        }
    }

    @Nested
    @DisplayName("Credenciales de prueba (dev/sesiones)")
    class DevSessions {

        private final TokenIssuer issuer = user -> new TokenIssuer.IssuedToken("token-" + user.playerId(),
                Duration.ofMinutes(15), NOW.plus(Duration.ofMinutes(15)));

        // Jugadores a los que se pidió entregar la colección inicial.
        private final List<UUID> deliveries = new ArrayList<>();
        private final InitialCollectionDelivery recordingDelivery = playerId -> {
            deliveries.add(playerId);
            return InitialCollectionStatus.DELIVERED;
        };

        @Test
        @DisplayName("ADMIN y PLAYER se crean como jugadores reales con proveedor dev")
        void createsRealDevPlayers() {
            IssueDevSessionUseCase useCase = new IssueDevSessionUseCase(
                    service(Set.of(new ProviderIdentity("dev", "dev-admin"))), recordingDelivery, issuer);

            Session admin = useCase.execute(Role.ADMIN).session();
            Session player = useCase.execute(Role.PLAYER).session();

            assertThat(admin.player().role()).isEqualTo(Role.ADMIN);
            assertThat(player.player().role()).isEqualTo(Role.PLAYER);
            assertThat(players.created()).containsOnlyKeys(new ProviderIdentity("dev", "dev-admin"),
                    new ProviderIdentity("dev", "dev-player"));
        }

        @Test
        @DisplayName("Si la configuración no declara dev:dev-admin, no entrega credenciales de otro rol")
        void refusesMismatchedRole() {
            IssueDevSessionUseCase useCase = new IssueDevSessionUseCase(service(Set.of()), recordingDelivery, issuer);

            assertThatThrownBy(() -> useCase.execute(Role.ADMIN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.admin.identities");
        }

        @Test
        @DisplayName("El jugador de prueba PLAYER recibe la colección inicial por la misma vía y su estado; el ADMIN de prueba no")
        void onlyDevPlayerReceivesCollection() {
            IssueDevSessionUseCase useCase = new IssueDevSessionUseCase(
                    service(Set.of(new ProviderIdentity("dev", "dev-admin"))), recordingDelivery, issuer);

            DevSessionResult admin = useCase.execute(Role.ADMIN);
            DevSessionResult player = useCase.execute(Role.PLAYER);

            assertThat(deliveries).containsExactly(player.session().player().id());
            assertThat(player.initialCollection()).isEqualTo(InitialCollectionStatus.DELIVERED);
            assertThat(admin.initialCollection()).isNull();
        }

        @Test
        @DisplayName("Si la entrega del jugador de prueba falla, la sesión de prueba se abre igual")
        void failedDeliveryDoesNotBlockDevSession() {
            IssueDevSessionUseCase useCase = new IssueDevSessionUseCase(service(Set.of()), playerId -> {
                throw new IllegalStateException("falla simulada");
            }, issuer);

            DevSessionResult player = useCase.execute(Role.PLAYER);

            assertThat(player.session().token().value()).isNotBlank();
            assertThat(player.initialCollection()).isEqualTo(InitialCollectionStatus.PENDING_ERROR);
        }
    }
}
