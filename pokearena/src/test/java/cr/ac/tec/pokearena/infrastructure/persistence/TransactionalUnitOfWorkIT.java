package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.error.ValidationException;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: verificar la frontera transaccional {@link UnitOfWork} sobre PostgreSQL real (sección 4.3).
 * Responsabilidades: comprobar que el trabajo se confirma cuando termina bien y se revierte por
 * completo cuando lanza una excepción, que llega intacta a quien llamó.
 * Referencia: sección 4.3.
 *
 * <p>A diferencia de {@code FlywayMigrationIT}, esta clase NO es {@code @Transactional}: si la
 * prueba abriera su propia transacción, UnitOfWork se uniría a ella y no se podría observar la
 * confirmación real. Por eso limpia sus filas al terminar cada prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Unidad de trabajo transaccional")
class TransactionalUnitOfWorkIT extends PostgresIntegrationTestBase {

    // Marca las filas de esta prueba para contarlas y borrarlas sin tocar otras.
    private static final String MARKER = "unit-of-work-it@pokearena.test";

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTestRows() {
        jdbc.update("DELETE FROM player WHERE email = ?", MARKER);
    }

    @Test
    @DisplayName("Confirma los cambios y devuelve el resultado cuando el trabajo termina bien")
    void commitsOnSuccess() {
        Integer inserted = unitOfWork.execute(() -> insertPlayer() + insertPlayer());

        assertThat(inserted).isEqualTo(2);
        assertThat(countPlayers()).isEqualTo(2);
    }

    @Test
    @DisplayName("Revierte todos los cambios si el trabajo lanza una excepción, y la propaga sin cambios")
    void rollsBackOnException() {
        ValidationException failure = ValidationException.missingField("ability", "Debe elegir una habilidad.");

        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            insertPlayer();
            insertPlayer();
            throw failure;
        })).isSameAs(failure);

        // Ninguna de las dos filas queda: no hay escritura parcial.
        assertThat(countPlayers()).isZero();
    }

    @Test
    @DisplayName("Revierte si la propia base rechaza una escritura a mitad del trabajo")
    void rollsBackOnDatabaseError() {
        assertThatThrownBy(() -> unitOfWork.execute(() -> {
            insertPlayer();
            // Viola ck_player_cube_balance_non_negative después de una escritura válida.
            return jdbc.update("INSERT INTO player (email, role, cube_balance, created_at) "
                    + "VALUES (?, 'PLAYER', -1, now())", MARKER);
        })).hasMessageContaining("ck_player_cube_balance_non_negative");

        assertThat(countPlayers()).isZero();
    }

    private int insertPlayer() {
        return jdbc.update("INSERT INTO player (email, role, created_at) VALUES (?, 'PLAYER', now())", MARKER);
    }

    private Integer countPlayers() {
        return jdbc.queryForObject("SELECT count(*) FROM player WHERE email = ?", Integer.class, MARKER);
    }
}
