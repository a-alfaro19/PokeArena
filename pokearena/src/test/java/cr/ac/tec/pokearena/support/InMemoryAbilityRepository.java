package cr.ac.tec.pokearena.support;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.Ability;
import cr.ac.tec.pokearena.domain.repository.AbilityRepository;

/**
 * Propósito: repositorio de habilidades en memoria para las pruebas unitarias (sin Spring ni base).
 * Responsabilidades: devolver las habilidades de una tabla que la prueba controla, como si todas
 * estuvieran activas, y listarlas ordenadas por código como la consulta real.
 * Referencia: sección 7.1 (paso 6) y {@code GET /api/v1/opciones/creacion}.
 */
public final class InMemoryAbilityRepository implements AbilityRepository {

    private final Map<String, Ability> table;

    /** @param table habilidades por código; la prueba puede modificarla después */
    public InMemoryAbilityRepository(Map<String, Ability> table) {
        this.table = table;
    }

    @Override
    public Optional<Ability> findActiveByCode(String code) {
        return Optional.ofNullable(table.get(code));
    }

    @Override
    public List<Ability> findAllActive() {
        return table.values().stream().sorted(Comparator.comparing(Ability::code)).toList();
    }
}
