package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
// This test commits real transactions; do not expose its fixtures to other repository tests.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentRepositoryLockingTest {

    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void findByExternalIdForUpdateWaitsForCommitAndReadsCommittedState() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String externalId = transaction.execute(status -> {
            Environment environment = environmentRepository.save(new Environment("DEV-" + UUID.randomUUID()));
            System system = systemRepository.save(new System("SYSTEM-" + UUID.randomUUID()));
            Component component = componentRepository.save(new Component("service", system));
            return deploymentRepository.save(TestDataFactory.createDeployment(environment, component,
                    ZonedDateTime.now(), TestDataFactory.createDeploymentTarget())).getExternalId();
        });
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<DeploymentState> second = transaction.execute(status -> {
                Deployment locked = deploymentRepository.findByExternalIdForUpdate(externalId).orElseThrow();
                Future<DeploymentState> waiting = executor.submit(() ->
                        new TransactionTemplate(transactionManager).execute(secondStatus -> {
                            secondTransactionStarted.countDown();
                            return deploymentRepository.findByExternalIdForUpdate(externalId).orElseThrow().getState();
                        }));
                await(secondTransactionStarted);
                // No update has been flushed yet: the SELECT FOR UPDATE itself must hold the lock.
                assertThatExceptionOfType(TimeoutException.class)
                        .isThrownBy(() -> waiting.get(300, TimeUnit.MILLISECONDS));
                locked.success(ZonedDateTime.now(), "committed by first transaction");
                return waiting;
            });

            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(DeploymentState.SUCCESS);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Executor did not terminate after deployment locking test");
                }
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("second transaction started").isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for second transaction", ex);
        }
    }
}
