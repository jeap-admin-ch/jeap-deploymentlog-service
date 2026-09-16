package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentationStructureLockTest {

    @Test
    void doesNotKeepDatabaseTransactionOpenWhileReconciliationCallsConfluence() {
        DocgenLocks docgenLocks = mock(DocgenLocks.class);
        List<String> events = new ArrayList<>();
        when(docgenLocks.runWithDocumentationStructureLock(any())).thenAnswer(invocation -> {
            events.add("shedlock-acquired");
            Object result = invocation.<java.util.function.Supplier<?>>getArgument(0).get();
            events.add("shedlock-released");
            return result;
        });
        DocumentationStructureLock lock = new DocumentationStructureLock(docgenLocks);

        String result = lock.runLocked(() -> {
            events.add("reconciled");
            return "result";
        });

        assertThat(result).isEqualTo("result");
        assertThat(events).containsExactly("shedlock-acquired", "reconciled", "shedlock-released");
    }

    @Test
    void returnsEmptyWhenShedLockIsBusy() {
        DocgenLocks docgenLocks = mock(DocgenLocks.class);
        when(docgenLocks.tryRunWithDocumentationStructureLock(any())).thenReturn(Optional.empty());
        DocumentationStructureLock lock = new DocumentationStructureLock(docgenLocks);

        Optional<String> result = lock.tryRunLocked(() -> "result");

        assertThat(result).isEmpty();
    }
}
