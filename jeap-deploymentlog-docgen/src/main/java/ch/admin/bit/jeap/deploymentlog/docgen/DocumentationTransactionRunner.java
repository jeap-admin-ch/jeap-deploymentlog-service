package ch.admin.bit.jeap.deploymentlog.docgen;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Starts the database transaction only after a documentation lock has been acquired.
 */
@Component
public class DocumentationTransactionRunner {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T run(Supplier<T> task) {
        return task.get();
    }
}
