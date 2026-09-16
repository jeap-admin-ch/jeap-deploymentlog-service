package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLocks;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
public class DocumentationStructureLock {

    private final DocgenLocks docgenLocks;

    public DocumentationStructureLock(DocgenLocks docgenLocks) {
        this.docgenLocks = docgenLocks;
    }

    public <T> T runLocked(Supplier<T> task) {
        return docgenLocks.runWithDocumentationStructureLock(task);
    }

    public <T> Optional<T> tryRunLocked(Supplier<T> task) {
        return docgenLocks.tryRunWithDocumentationStructureLock(task);
    }
}
