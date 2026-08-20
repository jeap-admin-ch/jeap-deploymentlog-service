package ch.admin.bit.jeap.deploymentlog.docgen.service;

import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Ensures that mutually-exclusive docgen jobs (generating pages for the same system) are executed one after each
 * other, even when the app is running on multiple instances.
 */
@Component
@Slf4j
class DocgenLocks {

    private static final String LOCK_NAME_PREFIX = "docgen-";
    private static final Duration LOCK_RETRY_WAIT_DURATION = Duration.ofSeconds(3);
    // The lock is kept alive for as long as the docgen run is in progress, so this duration only has to cover the
    // time between two extensions. Keeping it short means that a lock left behind by an instance that died without
    // releasing it blocks docgen for that system for at most this duration. It must not go below
    // MIN_LOCK_AT_MOST_FOR though, as KeepAliveLockProvider rejects anything shorter.
    static final Duration MIN_LOCK_AT_MOST_FOR = Duration.ofSeconds(30);
    private static final Duration DEFAULT_LOCK_AT_MOST_FOR = Duration.ofMinutes(2);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ZERO;
    // One thread per docgen thread (see DeploymentAsyncExecutorConfiguration), so that a slow lock extension cannot
    // delay the extension of the locks held by the other docgen runs
    private static final int LOCK_EXTENDER_THREADS = 10;
    // Wait at most this duration until giving up trying to acquire the lock
    private Duration tryAcquireTimeout = Duration.ofMinutes(3);
    private Duration lockAtMostFor = DEFAULT_LOCK_AT_MOST_FOR;

    private final LockProvider lockProvider;
    private final ScheduledExecutorService lockExtender;

    DocgenLocks(LockProvider lockProvider) {
        this.lockExtender = newLockExtender();
        this.lockProvider = keepAliveLockProvider(lockProvider, lockExtender);
    }

    /**
     * A docgen run can take longer than {@link #LOCK_AT_MOST_FOR} when confluence updates have to be retried. Letting
     * the lock expire underneath a running task would allow a second run for the same system to start concurrently -
     * which produces exactly the page update conflicts that made the run slow in the first place. Shedlock extends
     * the lock periodically for as long as it is held instead.
     */
    static LockProvider keepAliveLockProvider(LockProvider lockProvider, ScheduledExecutorService lockExtender) {
        if (lockProvider instanceof ExtensibleLockProvider extensibleLockProvider) {
            return new KeepAliveLockProvider(extensibleLockProvider, lockExtender);
        }
        log.warn("Lock provider {} cannot extend locks - a long running docgen run will lose its lock",
                lockProvider.getClass().getName());
        return lockProvider;
    }

    private static ScheduledExecutorService newLockExtender() {
        LockExtenderExecutor lockExtender = new LockExtenderExecutor();
        // Locks are released long before their extension would have been due again
        lockExtender.setRemoveOnCancelPolicy(true);
        return lockExtender;
    }

    /**
     * Shedlock schedules the lock extension with {@link ScheduledExecutorService#scheduleAtFixedRate}, which silently
     * suppresses all further executions as soon as one of them throws. A single failing extension - a short database
     * hiccup is enough - would therefore stop keeping the lock alive for the rest of the docgen run, without any
     * trace in the log. Swallowing the exception here keeps the extension scheduled and makes the failure visible.
     */
    private static final class LockExtenderExecutor extends ScheduledThreadPoolExecutor {

        private LockExtenderExecutor() {
            super(LOCK_EXTENDER_THREADS, runnable -> {
                Thread thread = new Thread(runnable, "DocgenLockExtender");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return super.scheduleAtFixedRate(keepScheduledOnFailure(command), initialDelay, period, unit);
        }

        private static Runnable keepScheduledOnFailure(Runnable command) {
            return () -> {
                try {
                    command.run();
                } catch (Exception ex) {
                    log.error("Failed to extend a docgen lock - it might expire while docgen is still running", ex);
                }
            };
        }
    }

    @PreDestroy
    void shutdown() {
        lockExtender.shutdownNow();
    }

    /**
     * Runs the task if the lock can be acquired before {@link #tryAcquireTimeout} passed. Otherwise, the task is not
     * executed. This is acceptable for page generating tasks as the risk of not being able to acquire the lock for a
     * long time is very low, and the scheduled job generating missing deployment pages will pick up and execute the
     * task later.
     */
    void runIfLockAquiredBeforeTimeout(String systemName, Runnable task) {
        String lockName = LOCK_NAME_PREFIX + systemName;
        tryAcquireLockWithTimeout(lockName).ifPresentOrElse(
                lock -> runLockedTask(task,lockName, lock),
                () -> log.warn("Unable to aquire lock {}, not running docgen. Pages will be generated by scheduled job.", lockName));

    }

    private void runLockedTask(Runnable task, String lockName, SimpleLock lock) {
        try {
            log.info("Acquired lock {}, running task", lockName);
            task.run();
        } finally {
            releaseLock(lock, lockName);
        }
    }

    /**
     * Releasing the lock must not fail the docgen run. Shedlock invalidates a lock as soon as extending it has been
     * attempted, also when the extension failed, so releasing it afterwards throws - and that exception would
     * otherwise escape and abort the remaining systems of a batch. The lock is gone in that case anyway.
     */
    private static void releaseLock(SimpleLock lock, String lockName) {
        try {
            log.info("Releasing lock {}", lockName);
            lock.unlock();
        } catch (Exception ex) {
            log.warn("Failed to release lock {} - it was probably lost while the task was running", lockName, ex);
        }
    }

    @SneakyThrows
    private Optional<SimpleLock> tryAcquireLockWithTimeout(String lockName) {
        LocalDateTime startedAt = LocalDateTime.now();
        for (int i = 1; ; i++) {
            Optional<SimpleLock> lock = lockProvider.lock(newLockConfiguration(lockName));
            if (lock.isPresent() || waitedLongEnough(startedAt)) {
                return lock;
            }
            log.info("Docgen for this system name is busy - waiting to aquire lock {} (retry #{})...", lockName, i);
            Thread.sleep(LOCK_RETRY_WAIT_DURATION.toMillis());
        }
    }

    private boolean waitedLongEnough(LocalDateTime startedAt) {
        return LocalDateTime.now().isAfter(startedAt.plus(tryAcquireTimeout));
    }

    private LockConfiguration newLockConfiguration(String lockName) {
        return new LockConfiguration(
                Instant.now(), lockName.toLowerCase(), lockAtMostFor, LOCK_AT_LEAST_FOR);
    }

    // For usage in tests
    void setTryAcquireTimeout(Duration tryAcquireTimeout) {
        this.tryAcquireTimeout = tryAcquireTimeout;
    }

    // For usage in tests
    void setLockAtMostFor(Duration lockAtMostFor) {
        this.lockAtMostFor = lockAtMostFor;
    }
}
