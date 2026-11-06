package cr.ac.tec.pokearena.domain.repository;

import java.util.List;
import java.util.Optional;

import cr.ac.tec.pokearena.domain.model.Card;
import cr.ac.tec.pokearena.domain.model.CardSearchCriteria;
import cr.ac.tec.pokearena.domain.model.StoredCard;

/**
 * Propósito: puerto de persistencia de cartas (patrón Repository, sección 4.4).
 * Responsabilidades: entregar el siguiente consecutivo de un tipo, guardar cartas, consultar la
 * cadena de evoluciones que necesita {@code EvolutionRules} y buscar cartas por criterios.
 * Referencia: sección 7.1 (paso 10 y "Código único") y sección 7.3 (búsqueda con paginación de 5.5).
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

    /**
     * Cartas que cumplen todos los criterios (AND), disponibles o no, con {@code id} mayor que
     * {@code afterId}, en orden de {@code id} ascendente y como máximo {@code maxRows} (sección 5.5).
     * La aplicación pide una fila más que el tamaño de la página para saber si hay otra.
     *
     * @param afterId último {@code id} de la página anterior; 0 para empezar desde el principio
     */
    List<StoredCard> search(CardSearchCriteria criteria, long afterId, int maxRows);
}
