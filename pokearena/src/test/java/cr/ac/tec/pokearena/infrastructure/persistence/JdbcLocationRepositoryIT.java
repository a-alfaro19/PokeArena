package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: verificar la lectura del catálogo disponible por {@link JdbcLocationRepository}.
 * Responsabilidades: comprobar filtros de disponibilidad/actividad, orden estable y mapeo de efecto y energía.
 * Referencia: sección 7.3, paso 2, y Anexo A.14.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de ubicaciones disponibles (JDBC)")
class JdbcLocationRepositoryIT extends PostgresIntegrationTestBase {

    private static final int TEST_CARD_LIMIT = 3;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> inserted = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        inserted.forEach(id -> jdbc.update("DELETE FROM location WHERE id = ?", id));
    }

    @Test
    @DisplayName("Devuelve solo ubicaciones disponibles y activas, ordenadas por id, con sus reglas cargadas")
    void findAllAvailableFiltersAndMaps() {
        long later = insertLocation("Disponible después", true, true, null, "{}", "FIXED", 2);
        insertLocation("No disponible", false, true, null, "{}", "NONE", 0);
        insertLocation("Inactiva", true, false, null, "{}", "NONE", 0);
        long first = insertLocation("Disponible primero", true, true, "TURN_RESTRICTION",
                "{\"turns\":[1,2,3,4,5,6]}", "PER_CARD", 1);

        List<AvailableLocation> result = locations.findAllAvailable();

        assertThat(result).extracting(AvailableLocation::id).containsExactly(later, first);
        assertThat(result.getLast().location().fields().extraEnergy())
                .isEqualTo(new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 1));
        assertThat(result.getLast().location().optionalEffect().orElseThrow().code())
                .isEqualTo("TURN_RESTRICTION");
        assertThat(result.getLast().location().optionalEffect().orElseThrow().parameters())
                .isEqualTo(Map.of("turns", List.of(1, 2, 3, 4, 5, 6)));
    }

    private long insertLocation(String name, boolean available, boolean active, String effectType,
            String effectParameters, String energyMode, int energyAmount) {
        Long id = jdbc.queryForObject("""
                INSERT INTO location (name, description, effect_type, effect_parameters, card_limit,
                                      extra_energy_mode, extra_energy_amount, image_large_url, image_small_url,
                                      image_status, available, active, created_at)
                VALUES (?, 'Descripción de prueba', ?, ?::jsonb, ?, ?, ?, '/large.png', '/small.png',
                        'READY', ?, ?, now())
                RETURNING id
                """, Long.class, name, effectType, effectParameters, TEST_CARD_LIMIT,
                energyMode, energyAmount, available, active);
        inserted.add(id);
        return id;
    }
}
