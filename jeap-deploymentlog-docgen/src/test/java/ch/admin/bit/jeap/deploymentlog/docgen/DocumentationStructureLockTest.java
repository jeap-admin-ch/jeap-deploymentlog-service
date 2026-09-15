package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLocks;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentationStructureLockTest {

    @Test
    void commitsReconciliationTransactionBeforeReleasingShedLock() {
        DocgenLocks docgenLocks = mock(DocgenLocks.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        List<String> events = new ArrayList<>();
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            events.add("transaction-started");
            return transactionStatus;
        });
        when(docgenLocks.runWithDocumentationStructureLock(any())).thenAnswer(invocation -> {
            events.add("shedlock-acquired");
            Object result = invocation.<java.util.function.Supplier<?>>getArgument(0).get();
            events.add("shedlock-released");
            return result;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            events.add("transaction-committed");
            return null;
        }).when(transactionManager).commit(transactionStatus);
        DocumentationStructureLock lock = new DocumentationStructureLock(docgenLocks, transactionManager);

        String result = lock.runLocked(() -> {
            events.add("reconciled");
            return "result";
        });

        assertThat(result).isEqualTo("result");
        assertThat(events).containsExactly(
                "shedlock-acquired",
                "transaction-started",
                "reconciled",
                "transaction-committed",
                "shedlock-released");
    }

    @Test
    void returnsEmptyWithoutStartingTransactionWhenShedLockIsBusy() {
        DocgenLocks docgenLocks = mock(DocgenLocks.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(docgenLocks.tryRunWithDocumentationStructureLock(any())).thenReturn(Optional.empty());
        DocumentationStructureLock lock = new DocumentationStructureLock(docgenLocks, transactionManager);

        Optional<String> result = lock.tryRunLocked(() -> "result");

        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(transactionManager);
    }
}
