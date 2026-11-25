package cr.ac.tec.pokearena.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;
import cr.ac.tec.pokearena.domain.model.AvailableLocation;
import cr.ac.tec.pokearena.domain.model.ExtraEnergy;
import cr.ac.tec.pokearena.domain.model.Location;
import cr.ac.tec.pokearena.domain.model.LocationEffect;
import cr.ac.tec.pokearena.domain.model.LocationFields;
import cr.ac.tec.pokearena.domain.repository.LocationRepository;
import cr.ac.tec.pokearena.support.PostgresIntegrationTestBase;

/**
 * Propósito: verificar la lectura de ubicaciones disponibles con PostgreSQL real.
 * Responsabilidades: probar el filtro disponible/activa, el orden por id y el mapeo de parámetros JSONB.
 * Referencia: sección 7.3, paso 2; criterio C.10-E1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Repositorio de ubicaciones disponibles (JDBC)")
class JdbcLocationRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant CREATED_AT = Instant.parse("2026-11-24T12:00:00Z");
    private static final String NAME_PREFIX = "fase3-location-";

    @Autowired
    private LocationRepository locations;

    @Autowired
    private UnitOfWork unitOfWork;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> savedIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : savedIds) {
            jdbc.update("DELETE FROM location WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("C.10-E1: lista ubicaciones activas disponibles ordenadas por id, incluyendo su efecto")
    void listsAvailableLocationsAndMapsEffect() {
        LocationEffect effect = new LocationEffect("TURN_RESTRICTION",
                Map.of("turns", List.of(1, 2, 3, 4, 5, 6)));
        long availableId = save(location(NAME_PREFIX + "available", effect,
                new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 2)));
        long inactiveId = save(location(NAME_PREFIX + "inactive", null, ExtraEnergy.none()));
        long unavailableId = save(location(NAME_PREFIX + "unavailable", null, ExtraEnergy.none()));
        jdbc.update("UPDATE location SET active = false WHERE id = ?", inactiveId);
        jdbc.update("UPDATE location SET available = false WHERE id = ?", unavailableId);

        List<AvailableLocation> found = locations.findAllAvailable();

        assertThat(found).extracting(AvailableLocation::id).isSorted();
        assertThat(found).extracting(AvailableLocation::id).contains(availableId)
                .doesNotContain(inactiveId, unavailableId);
        AvailableLocation loaded = found.stream().filter(location -> location.id() == availableId)
                .findFirst().orElseThrow();
        assertThat(loaded.location().effect()).isEqualTo(effect);
        assertThat(loaded.location().fields().extraEnergy())
                .isEqualTo(new ExtraEnergy(ExtraEnergy.Mode.PER_CARD, 2));
    }

    private long save(Location location) {
        long id = unitOfWork.execute(() -> locations.save(location));
        savedIds.add(id);
        return id;
    }

    private static Location location(String name, LocationEffect effect, ExtraEnergy extraEnergy) {
        LocationFields fields = new LocationFields(name, "Descripción " + name, 3, extraEnergy);
        return Location.create(fields, effect, "/images/locations/" + name + "/large.png",
                "/images/locations/" + name + "/small.png", CREATED_AT);
    }
}
