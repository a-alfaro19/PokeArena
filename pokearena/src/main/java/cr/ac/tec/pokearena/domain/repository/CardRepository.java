package cr.ac.tec.pokearena.domain.repository;

import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.Card;

/**
 * Propósito: puerto de persistencia de cartas (patrón Repository, sección 4.4).
 * Responsabilidades: entregar el siguiente consecutivo de un tipo, guardar cartas y consultar la
 * cadena de evoluciones que necesita {@code EvolutionRules}.
 * Referencia: sección 7.1 (paso 10 y "Código único").
 *
 * <p>{@link #nextNumber} y {@link #save} deben llamarse dentro del mismo {@code UnitOfWork}: así, si
 * la transacción se revierte, el consecutivo no se consume.
 */
public interface CardRepository {

    /**
     * Incrementa y devuelve el consecutivo del tipo, de forma atómica: dos transacciones simultáneas
     * del mismo tipo reciben números distintos.
     */
    int nextNumber(String typeCode);

    /** Guarda una carta nueva. */
    void save(Card card);

    /** Destino de evolución de la habilidad de la carta {@code cardCode}, si la carta existe y lo tiene. */
    Optional<String> evolutionTargetOf(String cardCode);
}
