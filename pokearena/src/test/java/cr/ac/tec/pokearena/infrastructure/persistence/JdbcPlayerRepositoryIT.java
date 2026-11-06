package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.model.Player;
import cr.ac.tec.pokearena.domain.model.ProviderIdentity;
import cr.ac.tec.pokearena.domain.model.Role;
import cr.ac.tec.pokearena.domain.repository.PlayerRepository;
import cr.ac.tec.pokearena.domain.rule.AdminIdentities;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: verificar sobre PostgreSQL real que una identidad tiene una sola cuenta (sección 6).
 * Responsabilidades: comprobar la búsqueda o creación de jugadores, también con varias solicitudes
 * simultáneas de la misma identidad nueva, y que no quedan jugadores huérfanos.
 * Referencia: sección 8.1, paso 5, y sección 9 ("una identidad tiene una sola cuenta").
 *
 * <p>Cada prueba usa una identidad nueva y borra sus filas al terminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de jugadores: una cuenta por identidad")
class JdbcPlayerRepositoryIT extends PostgresIntegrationTestBase {

    // Solicitudes simultáneas de la misma identidad nueva.
    private static final int CONCURRENT_LOGINS = 8;

    // Nombre que marca las filas de esta prueba, para contarlas y borrarlas.
    private static final String MARKER_NAME = "jugador-prueba-repositorio";

    @Autowired
    private PlayerAccountService accounts;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PlayerRepository players;

    @Autowired
    private JdbcTemplate jdbc;

    private final ProviderIdentity identity = new ProviderIdentity("google", "sub-" + UUID.randomUUID());

    @AfterEach
    void removeTestRows() {
        jdbc.update("DELETE FROM player_identity WHERE provider = ? AND provider_subject = ?",
                identity.provider(), identity.subject());
        jdbc.update("DELETE FROM player WHERE display_name = ?", MARKER_NAME);
    }

    @Test
    @DisplayName("El primer ingreso crea el jugador y su identidad; el segundo lo reutiliza")
    void createsThenReuses() {
        Player first = accounts.findOrCreate(identity, MARKER_NAME, "ana@example.com");
        Player second = accounts.findOrCreate(identity, MARKER_NAME, "ana@example.com");

        assertThat(second).isEqualTo(first);
        assertThat(first.role()).isEqualTo(Role.PLAYER);
        assertThat(first.email()).isEqualTo("ana@example.com");
        assertThat(identityRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un cambio de correo en el proveedor no crea otra cuenta: se identifica por sub")
    void emailChangeKeepsAccount() {
        Player first = accounts.findOrCreate(identity, MARKER_NAME, "ana@example.com");
        Player second = accounts.findOrCreate(identity, MARKER_NAME, "ana.nueva@example.com");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(markedPlayers()).isEqualTo(1);
    }

    @Test
    @DisplayName("El rol recalculado en el ingreso queda guardado en la base: PLAYER a ADMIN y de vuelta")
    void recalculatedRoleIsPersisted() {
        PlayerAccountService identityIsAdmin = new PlayerAccountService(unitOfWork, players,
                new AdminIdentities(Set.of(identity)), Instant::now);
        Player created = accounts.findOrCreate(identity, MARKER_NAME, null);
        assertThat(storedRole(created)).isEqualTo("PLAYER");

        // La identidad entra a la configuración: en su siguiente ingreso es ADMIN, misma cuenta.
        Player promoted = identityIsAdmin.findOrCreate(identity, MARKER_NAME, null);
        assertThat(promoted.id()).isEqualTo(created.id());
        assertThat(storedRole(created)).isEqualTo("ADMIN");

        // La identidad sale de la configuración: vuelve a PLAYER.
        accounts.findOrCreate(identity, MARKER_NAME, null);
        assertThat(storedRole(created)).isEqualTo("PLAYER");
        assertThat(markedPlayers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Varios ingresos simultáneos de una identidad nueva crean exactamente una cuenta")
    void concurrentLoginsCreateOneAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_LOGINS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Player>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_LOGINS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return accounts.findOrCreate(identity, MARKER_NAME, null);
                }));
            }
            // Todas las solicitudes salen al mismo tiempo.
            start.countDown();

            List<UUID> ids = new ArrayList<>();
            for (Future<Player> result : results) {
                ids.add(result.get().id());
            }

            assertThat(ids).hasSize(CONCURRENT_LOGINS).containsOnly(ids.getFirst());
            assertThat(identityRows()).isEqualTo(1);
            // Las solicitudes que perdieron la carrera borraron su jugador: no quedan huérfanos.
            assertThat(markedPlayers()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private String storedRole(Player player) {
        return jdbc.queryForObject("SELECT role FROM player WHERE id = ?", String.class, player.id());
    }

    private Integer identityRows() {
        return jdbc.queryForObject("SELECT count(*) FROM player_identity WHERE provider = ? AND provider_subject = ?",
                Integer.class, identity.provider(), identity.subject());
    }

    private Integer markedPlayers() {
        return jdbc.queryForObject("SELECT count(*) FROM player WHERE display_name = ?", Integer.class, MARKER_NAME);
    }
}
