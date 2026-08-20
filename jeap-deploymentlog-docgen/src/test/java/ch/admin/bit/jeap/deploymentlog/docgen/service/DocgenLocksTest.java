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
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    void keepAliveLockProvider_extendsTheLockWhileTheTaskIsRunning() {
        ScheduledExecutorService lockExtenderMock = newLockExtenderMock();
        doReturn(Optional.of(lockMock)).when(extensibleLockProviderMock).lock(any());
        doReturn(Optional.of(lockMock)).when(lockMock).extend(any(), any());
        LockProvider keepAlive = DocgenLocks.keepAliveLockProvider(extensibleLockProviderMock, lockExtenderMock);

        keepAlive.lock(newLockConfiguration());

        // Running the scheduled extension must extend the underlying lock, that is the whole point of the wrapping
        runScheduledExtension(lockExtenderMock);
        verify(lockMock).extend(any(), any());
    }

    @Test
    void keepAliveLockProvider_stillReleasesTheUnderlyingLockAfterALostExtension() {
        ScheduledExecutorService lockExtenderMock = newLockExtenderMock();
        doReturn(Optional.of(lockMock)).when(extensibleLockProviderMock).lock(any());
        doReturn(Optional.empty()).when(lockMock).extend(any(), any());
        // In production this is the underlying lock refusing to be unlocked after shedlock invalidated it
        doThrow(new IllegalStateException("Lock docgen-systemname is not valid")).when(lockMock).unlock();
        LockProvider keepAlive = DocgenLocks.keepAliveLockProvider(extensibleLockProviderMock, lockExtenderMock);
        SimpleLock keepAliveLock = keepAlive.lock(newLockConfiguration()).orElseThrow();

        runScheduledExtension(lockExtenderMock);

        // Shedlock keeps releasing the lock it just invalidated, and lets the failure propagate. That is exactly why
        // DocgenLocks releases best-effort, see runIfLockAquiredBeforeTimeout_releasingALostLockDoesNotFailTheRun
        assertThatThrownBy(keepAliveLock::unlock).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lockExtender_keepsExtendingAfterAFailedExtension() throws Exception {
        ScheduledExecutorService lockExtender = DocgenLocks.newLockExtender();
        AtomicInteger executions = new AtomicInteger();
        try {
            // A periodic task is silently dropped by the JDK as soon as one execution throws
            lockExtender.scheduleAtFixedRate(() -> {
                executions.incrementAndGet();
                throw new IllegalStateException("extension failed");
            }, 0, 10, TimeUnit.MILLISECONDS);

            await().atMost(Duration.ofSeconds(5)).until(() -> executions.get() >= 3);
        } finally {
            lockExtender.shutdownNow();
        }
    }

    private static LockConfiguration newLockConfiguration() {
        return new LockConfiguration(Instant.now(), "docgen-systemname",
                DocgenLocks.MIN_LOCK_AT_MOST_FOR, Duration.ZERO);
    }

    private static ScheduledExecutorService newLockExtenderMock() {
        ScheduledExecutorService lockExtenderMock = mock(ScheduledExecutorService.class);
        // Shedlock cancels this future when it gives up on the lock
        doReturn(mock(ScheduledFuture.class)).when(lockExtenderMock)
                .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        return lockExtenderMock;
    }

    private static void runScheduledExtension(ScheduledExecutorService lockExtenderMock) {
        ArgumentCaptor<Runnable> extension = ArgumentCaptor.forClass(Runnable.class);
        verify(lockExtenderMock).scheduleAtFixedRate(extension.capture(), anyLong(), anyLong(), any());
        extension.getValue().run();
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
