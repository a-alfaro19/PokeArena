package cr.ac.tec.pokearena.application.port;

import java.util.function.Supplier;

/**
 * Propósito: frontera transaccional explícita de los casos de uso (sección 4.3, patrón Unit of Work).
 * Responsabilidades: ejecutar un bloque de trabajo dentro de una transacción: la confirma si el
 * bloque termina bien y la revierte si lanza cualquier excepción, que se propaga sin cambios.
 * Referencia: sección 4.3.
 *
 * <p>Existe para que los casos de uso no usen {@code @Transactional} ni ninguna otra anotación de
 * Spring (regla 2 de 4.2). La implementación vive en {@code infrastructure.persistence}.
 * Regla de uso: una transacción por caso de uso y sin efectos externos (archivos, Redis) dentro
 * del bloque, salvo lo indicado en cada historia.
 */
public interface UnitOfWork {

    /**
     * Ejecuta {@code work} en una transacción.
     *
     * @param work bloque que lee y escribe en la base
     * @param <T>  tipo del resultado del bloque
     * @return el resultado del bloque, ya confirmado
     */
    <T> T execute(Supplier<T> work);
}
