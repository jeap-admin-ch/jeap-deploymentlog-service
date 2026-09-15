package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLocks;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class DocumentationStructureLock {

    private final DocgenLocks docgenLocks;
    private final TransactionTemplate transactionTemplate;

    public DocumentationStructureLock(DocgenLocks docgenLocks, PlatformTransactionManager transactionManager) {
        this.docgenLocks = docgenLocks;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public <T> T runLocked(Supplier<T> task) {
        return docgenLocks.runWithDocumentationStructureLock(() -> Objects.requireNonNull(
                transactionTemplate.execute(status -> task.get())));
    }

    public <T> Optional<T> tryRunLocked(Supplier<T> task) {
        return docgenLocks.tryRunWithDocumentationStructureLock(() -> Objects.requireNonNull(
                transactionTemplate.execute(status -> task.get())));
    }
}
