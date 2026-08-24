package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowLifecycleService;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowStageProperties;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Import({FlowLifecycleService.class, FlowStageProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlowLifecycleConcurrencyTest {

    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private FlowLifecycleService flowLifecycleService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void closingTransitionWinsWhenItObtainsComponentLockFirst() throws Exception {
        Scenario scenario = createScenario();

        runConcurrentlyWithFirstLock(scenario.componentId(), scenario.olderDeploymentId(),
                scenario.winnerDeploymentId());

        assertFlowStates(scenario, FlowState.CLOSED, null);
    }

    @Test
    void abortingTransitionWinsWhenProductiveNewerFlowObtainsComponentLockFirst() throws Exception {
        Scenario scenario = createScenario();

        runConcurrentlyWithFirstLock(scenario.componentId(), scenario.winnerDeploymentId(),
                scenario.olderDeploymentId());

        assertFlowStates(scenario, FlowState.ABORTED, scenario.winnerFlowId());
    }

    private Scenario createScenario() {
        return transactionTemplate().execute(status -> {
            Environment prod = environmentRepository.save(new Environment("PROD-" + UUID.randomUUID()));
            prod.setProductive(true);
            ch.admin.bit.jeap.deploymentlog.domain.System system =
                    systemRepository.save(new ch.admin.bit.jeap.deploymentlog.domain.System("SYSTEM-" + UUID.randomUUID()));
            Component component = componentRepository.save(new Component("service", system));
            ZonedDateTime winningCommit = ZonedDateTime.parse("2026-08-20T12:00:00+02:00");

            Deployment olderDeployment = persistDeployment(component, prod, "1.0.0", winningCommit.minusDays(1));
            flowRepository.save(Flow.start(FlowType.NEW, olderDeployment, prod));
            Deployment winnerDeployment = persistDeployment(component, prod, "2.0.0", winningCommit);
            Flow winnerFlow = flowRepository.save(Flow.start(FlowType.NEW, winnerDeployment, prod));

            return new Scenario(component.getId(), olderDeployment.getId(), winnerDeployment.getId(),
                    winnerFlow.getId());
        });
    }

    private Deployment persistDeployment(Component component,
                                           Environment environment,
                                           String versionName,
                                           ZonedDateTime committedAt) {
        Deployment deployment = TestDataFactory.createDeployment(environment, component, ZonedDateTime.now(),
                versionName, committedAt, TestDataFactory.createDeploymentTarget());
        deployment.getDeploymentTypes().add(DeploymentType.CODE);
        return deploymentRepository.save(deployment);
    }

    private void runConcurrentlyWithFirstLock(UUID componentId,
                                              UUID firstDeploymentId,
                                              UUID secondDeploymentId)
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch secondIsReady = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transactionTemplate().executeWithoutResult(status -> {
                Deployment deployment = deploymentRepository.findById(firstDeploymentId).orElseThrow();
                deployment.success(ZonedDateTime.now(), "done");
                flowRepository.lockComponent(componentId);
                firstHasLock.countDown();
                await(secondIsReady);
                flowLifecycleService.process(deployment);
            }));
            Future<?> second = executor.submit(() -> {
                await(firstHasLock);
                transactionTemplate().executeWithoutResult(status -> {
                    Deployment deployment = deploymentRepository.findById(secondDeploymentId).orElseThrow();
                    deployment.success(ZonedDateTime.now(), "done");
                    secondIsReady.countDown();
                    flowLifecycleService.process(deployment);
                });
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Executor did not terminate after concurrent flow test");
                }
            }
        }
    }

    private void assertFlowStates(Scenario scenario, FlowState olderState, UUID expectedAbortedBy) {
        transactionTemplate().executeWithoutResult(status -> {
            Flow older = flowRepository.findByDeploymentId(scenario.olderDeploymentId()).orElseThrow();
            Flow winner = flowRepository.findByDeploymentId(scenario.winnerDeploymentId()).orElseThrow();
            assertThat(older.getState()).isEqualTo(olderState);
            assertThat(winner.getState()).isEqualTo(FlowState.CLOSED);
            if (expectedAbortedBy == null) {
                assertThat(older.getAbortedBy()).isNull();
            } else {
                assertThat(older.getAbortedBy().getId()).isEqualTo(expectedAbortedBy);
            }
        });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent flow transitions");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent flow transitions", e);
        }
    }

    private record Scenario(UUID componentId,
                            UUID olderDeploymentId,
                            UUID winnerDeploymentId,
                            UUID winnerFlowId) {
    }
}
