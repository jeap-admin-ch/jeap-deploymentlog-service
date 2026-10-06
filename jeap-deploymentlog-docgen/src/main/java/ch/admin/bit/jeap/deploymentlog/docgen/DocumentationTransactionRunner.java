package ch.admin.bit.jeap.deploymentlog.docgen;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Executes a short database-only unit of work. Never pass tasks that call Confluence or Jira.
 */
@Component
public class DocumentationTransactionRunner {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T run(Supplier<T> task) {
        return task.get();
    }
}
