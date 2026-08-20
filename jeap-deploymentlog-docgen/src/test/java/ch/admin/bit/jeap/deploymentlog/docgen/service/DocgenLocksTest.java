package ch.admin.bit.jeap.deploymentlog.docgen.service;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    void keepAliveLockProvider_wrapsAProviderThatCanExtendLocks() {
        ScheduledExecutorService lockExtender = Executors.newSingleThreadScheduledExecutor();
        try {
            LockProvider extensible = DocgenLocks.keepAliveLockProvider(extensibleLockProviderMock, lockExtender);
            LockProvider plain = DocgenLocks.keepAliveLockProvider(plainLockProviderMock, lockExtender);

            // Only an extensible provider can keep the lock alive while a slow docgen run is in progress
            assertThat(extensible).isInstanceOf(KeepAliveLockProvider.class);
            assertThat(plain).isSameAs(plainLockProviderMock);
        } finally {
            lockExtender.shutdownNow();
        }
    }

    @Test
    void runIfLockAquiredBeforeTimeout_acquiresLockWithADurationKeepAliveLockProviderAccepts() {
        doReturn(Optional.of(lockMock)).when(extensibleLockProviderMock).lock(any());
        DocgenLocks locks = new DocgenLocks(extensibleLockProviderMock);

        try {
            locks.runIfLockAquiredBeforeTimeout("systemName", () -> {});
        } finally {
            locks.shutdown();
        }

        // KeepAliveLockProvider rejects a shorter duration at runtime, so shortening it would break docgen entirely
        ArgumentCaptor<LockConfiguration> lockConfiguration = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(extensibleLockProviderMock).lock(lockConfiguration.capture());
        assertThat(lockConfiguration.getValue().getLockAtMostFor())
                .isGreaterThanOrEqualTo(DocgenLocks.MIN_LOCK_AT_MOST_FOR);
    }

    @Test
    void runIfLockAquiredBeforeTimeout_releasingALostLockDoesNotFailTheRun() {
        doReturn(Optional.of(lockMock)).when(extensibleLockProviderMock).lock(any());
        // Shedlock invalidates the lock as soon as extending it was attempted, so releasing it afterwards throws
        doThrow(new IllegalStateException("Lock docgen-systemname is not valid")).when(lockMock).unlock();
        DocgenLocks locks = new DocgenLocks(extensibleLockProviderMock);
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
