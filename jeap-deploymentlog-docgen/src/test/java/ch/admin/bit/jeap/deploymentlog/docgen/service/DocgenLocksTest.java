package ch.admin.bit.jeap.deploymentlog.docgen.service;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocgenLocksTest {

    @Mock
    private ExtensibleLockProvider extensibleLockProviderMock;

    @Mock
    private LockProvider plainLockProviderMock;

    @Mock
    private SimpleLock lockMock;

    @Test
    void runIfLockAquiredBeforeTimeout_keepsTheLockAliveWhileTheTaskIsRunning() {
        doReturn(Optional.of(lockMock)).when(extensibleLockProviderMock).lock(any());
        DocgenLocks locks = new DocgenLocks(extensibleLockProviderMock);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> taskExecuted.set(true));
        } finally {
            locks.shutdown();
        }

        // The lock is wrapped by shedlock's keep-alive lock, which extends it for as long as it is held and releases
        // the underlying lock when the docgen run is done
        assertThat(taskExecuted).isTrue();
        verify(lockMock).unlock();
    }

    @Test
    void runIfLockAquiredBeforeTimeout_runsTaskWithALockProviderThatCannotExtend() {
        doReturn(Optional.of(lockMock)).when(plainLockProviderMock).lock(any());
        DocgenLocks locks = new DocgenLocks(plainLockProviderMock);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> taskExecuted.set(true));
        } finally {
            locks.shutdown();
        }

        assertThat(taskExecuted).isTrue();
        verify(lockMock).unlock();
    }

    @Test
    void runIfLockAquiredBeforeTimeout_doesNotRunTaskWhenLockCannotBeAcquired() {
        doReturn(Optional.empty()).when(extensibleLockProviderMock).lock(any());
        DocgenLocks locks = new DocgenLocks(extensibleLockProviderMock);
        locks.setTryAcquireTimeout(Duration.ZERO);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> taskExecuted.set(true));
        } finally {
            locks.shutdown();
        }

        assertThat(taskExecuted).isFalse();
        verifyNoInteractions(lockMock);
    }
}
