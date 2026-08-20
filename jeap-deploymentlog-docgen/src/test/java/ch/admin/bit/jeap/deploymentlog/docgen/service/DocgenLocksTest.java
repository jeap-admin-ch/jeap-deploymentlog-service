package ch.admin.bit.jeap.deploymentlog.docgen.service;

import lombok.SneakyThrows;
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

    private static final Duration EXTEND_INTERVAL = Duration.ofMillis(20);
    private static final Duration TASK_DURATION = Duration.ofMillis(300);

    @Mock
    private LockProvider lockProviderMock;

    @Mock
    private SimpleLock lockMock;

    @Test
    void runIfLockAquiredBeforeTimeout_extendsLockWhileTaskIsRunning() {
        DocgenLocks locks = newDocgenLocks();
        doReturn(Optional.of(lockMock)).when(lockProviderMock).lock(any());
        doReturn(Optional.of(lockMock)).when(lockMock).extend(any(), any());

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> sleep(TASK_DURATION));
        } finally {
            locks.shutdown();
        }

        // A docgen run outliving the lock duration must keep its lock instead of letting a second run start
        verify(lockMock, atLeast(2)).extend(any(), any());
        verify(lockMock).unlock();
    }

    @Test
    void runIfLockAquiredBeforeTimeout_stopsExtendingOnceTheLockIsLost() {
        DocgenLocks locks = newDocgenLocks();
        doReturn(Optional.of(lockMock)).when(lockProviderMock).lock(any());
        doReturn(Optional.empty()).when(lockMock).extend(any(), any());

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> sleep(TASK_DURATION));
        } finally {
            locks.shutdown();
        }

        // Retrying on the lost lock would only repeat the same failure for the rest of the run
        verify(lockMock, times(1)).extend(any(), any());
        verify(lockMock).unlock();
    }

    @Test
    void runIfLockAquiredBeforeTimeout_doesNotRunTaskWhenLockCannotBeAcquired() {
        DocgenLocks locks = newDocgenLocks();
        locks.setTryAcquireTimeout(Duration.ZERO);
        doReturn(Optional.empty()).when(lockProviderMock).lock(any());
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> taskExecuted.set(true));
        } finally {
            locks.shutdown();
        }

        assertThat(taskExecuted).isFalse();
    }

    private DocgenLocks newDocgenLocks() {
        DocgenLocks locks = new DocgenLocks(lockProviderMock);
        locks.setLockExtendInterval(EXTEND_INTERVAL);
        return locks;
    }

    @SneakyThrows
    private static void sleep(Duration duration) {
        Thread.sleep(duration.toMillis());
    }
}
