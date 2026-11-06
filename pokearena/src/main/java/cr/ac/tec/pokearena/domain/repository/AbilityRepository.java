package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.Ability;

/**
 * Propósito: puerto de lectura de habilidades (sección 6, tabla ability).
 * Responsabilidades: encontrar una habilidad activa por su código, para asignarla a una carta.
 * Referencia: sección 7.1, paso 6.
 */
public interface AbilityRepository {

    /** Habilidad activa con ese código, si existe. */
    Optional<Ability> findActiveByCode(String code);
}
