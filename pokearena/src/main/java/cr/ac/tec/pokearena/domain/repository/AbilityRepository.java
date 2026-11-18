package cr.ac.tec.pokearena.domain.repository;

import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.Ability;

/**
 * Propósito: puerto de lectura de habilidades (sección 6, tabla ability).
 * Responsabilidades: encontrar una habilidad activa por su código, para asignarla a una carta, y listar
 * las habilidades activas para el formulario de Crear carta.
 * Referencia: sección 7.1, paso 6, y {@code GET /api/v1/opciones/creacion}.
 */
public interface AbilityRepository {

    /** Habilidad activa con ese código, si existe. */
    Optional<Ability> findActiveByCode(String code);

    /**
     * Habilidades activas, ordenadas por código: las mismas que acepta {@link #findActiveByCode}. Incluye
     * {@code HAB-000} "Sin Habilidad".
     */
    List<Ability> findAllActive();
}
