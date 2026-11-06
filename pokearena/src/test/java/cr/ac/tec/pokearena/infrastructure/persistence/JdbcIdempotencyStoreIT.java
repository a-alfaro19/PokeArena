package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.IdempotencyKey;
import cr.ac.tec.pokearena.application.port.StoredResponse;
import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.application.usecase.PlayerAccountService;
import cr.ac.tec.pokearena.domain.error.IdempotencyConflictException;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;
import cr.ac.tec.pokearena.support.TestPlayers;

/**
 * Propósito: verificar el almacén de claves de idempotencia sobre PostgreSQL real (sección 5.4).
 * Responsabilidades: comprobar que guarda y lee por (key, user_id, endpoint), que una clave repetida
 * se traduce al conflicto de dominio, que exige una transacción, que respeta la clave foránea a
 * player y que created_at viene del puerto Clock.
 * Referencia: sección 5.4 y sección 6 (tabla idempotency_key).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Almacén JDBC de claves de idempotencia")
class JdbcIdempotencyStoreIT extends PostgresIntegrationTestBase {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final String ENDPOINT = "POST /api/v1/cartas";
    // El cuerpo se escribe en la forma en que PostgreSQL devuelve un jsonb como texto (con espacio
    // después de los dos puntos), para poder compararlo tal cual. Al repetir una respuesta HTTP el
    // cuerpo se reconstruye con Jackson, así que ese espaciado no llega al cliente.
    private static final StoredResponse RESPONSE = new StoredResponse("hash-a", 201, "{\"code\": \"PKA-FUE-001\"}");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private PlayerAccountService accounts;

    private final List<UUID> createdPlayers = new ArrayList<>();
    private JdbcIdempotencyStore store;
    private UUID player;

    @BeforeEach
    void createPlayer() {
        // Reloj fijo para comprobar que created_at no sale de la base ni de Instant.now().
        store = new JdbcIdempotencyStore(jdbc, () -> FIXED_NOW);
        player = newPlayer();
    }

    @AfterEach
    void removePlayers() {
        TestPlayers.delete(jdbc, createdPlayers);
    }

    @Test
    @DisplayName("Guarda la respuesta y la lee por clave, usuario y operación, con created_at del reloj")
    void savesAndFinds() {
        IdempotencyKey key = new IdempotencyKey("clave-1", player, ENDPOINT);

        unitOfWork.execute(() -> {
            store.save(key, RESPONSE);
            return null;
        });

        assertThat(store.find(key)).contains(RESPONSE);
        assertThat(jdbc.queryForObject("SELECT created_at FROM idempotency_key WHERE key = ? AND user_id = ?",
                OffsetDateTime.class, "clave-1", player).toInstant()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("La misma clave con otro usuario u otra operación es otra clave")
    void keyIsScopedToUserAndEndpoint() {
        UUID otherPlayer = newPlayer();
        unitOfWork.execute(() -> {
            store.save(new IdempotencyKey("clave-1", player, ENDPOINT), RESPONSE);
            store.save(new IdempotencyKey("clave-1", otherPlayer, ENDPOINT), RESPONSE);
            store.save(new IdempotencyKey("clave-1", player, "POST /api/v1/ubicaciones"), RESPONSE);
            return null;
        });

        assertThat(store.find(new IdempotencyKey("clave-1", otherPlayer, ENDPOINT))).isPresent();
        assertThat(store.find(new IdempotencyKey("clave-2", player, ENDPOINT))).isEmpty();
    }

    @Test
    @DisplayName("Guardar una clave ya guardada lanza el conflicto de dominio, no un error de la base")
    void duplicateKeyBecomesConflict() {
        IdempotencyKey key = new IdempotencyKey("clave-1", player, ENDPOINT);
        unitOfWork.execute(() -> {
            store.save(key, RESPONSE);
            return null;
        });

        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            store.save(key, new StoredResponse("hash-b", 201, "{}"));
            return null;
        })).isInstanceOf(IdempotencyConflictException.class);

        // La respuesta original no cambió.
        assertThat(store.find(key)).contains(RESPONSE);
    }

    @Test
    @DisplayName("Guardar fuera de una transacción es un error de programación y no guarda nada")
    void saveRequiresTransaction() {
        IdempotencyKey key = new IdempotencyKey("clave-1", player, ENDPOINT);

        assertThatThrownBy(() -> store.save(key, RESPONSE)).isInstanceOf(IllegalStateException.class);
        assertThat(store.find(key)).isEmpty();
    }

    @Test
    @DisplayName("Una clave de un usuario que no existe en player es rechazada por la clave foránea")
    void unknownUserIsRejected() {
        IdempotencyKey key = new IdempotencyKey("clave-1", UUID.randomUUID(), ENDPOINT);

        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            store.save(key, RESPONSE);
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("fk_idempotency_key_player");
    }

    private UUID newPlayer() {
        UUID id = TestPlayers.create(accounts).id();
        createdPlayers.add(id);
        return id;
    }
}
