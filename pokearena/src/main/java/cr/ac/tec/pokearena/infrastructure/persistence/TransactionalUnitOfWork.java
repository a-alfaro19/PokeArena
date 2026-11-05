package cr.ac.tec.pokearena.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import cr.ac.tec.pokearena.application.port.UnitOfWork;

/**
 * Propósito: implementación de {@link UnitOfWork} con transacciones de Spring (sección 4.3).
 * Responsabilidades: abrir una transacción con {@link TransactionTemplate}, confirmarla si el
 * bloque termina bien y revertirla si lanza una excepción.
 * Referencia: sección 4.3.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Se usa la configuración por defecto de {@code TransactionTemplate}: propagación REQUIRED y
 *       el aislamiento por defecto de PostgreSQL (READ COMMITTED), como dice la sección 9. Donde
 *       haga falta "exactamente una vez" se usan restricciones y bloqueos de fila, no otro nivel.</li>
 *   <li>{@code TransactionTemplate} revierte ante cualquier {@code RuntimeException} o
 *       {@code Error}. Un {@link Supplier} no puede lanzar excepciones verificadas, así que eso
 *       cubre toda excepción posible del bloque.</li>
 *   <li>Con REQUIRED, si ya hay una transacción abierta el bloque se une a ella en lugar de abrir
 *       otra. Por eso un caso de uso no debe llamar a otro que también abra su UnitOfWork esperando
 *       transacciones separadas.</li>
 * </ul>
 */
@Component
public class TransactionalUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    public TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
