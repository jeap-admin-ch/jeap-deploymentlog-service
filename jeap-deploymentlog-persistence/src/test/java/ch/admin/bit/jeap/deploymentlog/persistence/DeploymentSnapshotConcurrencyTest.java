package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "jeap.deploymentlog.flow.enabled=false")
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentSnapshotConcurrencyTest {
    @Autowired private DeploymentRepository deployments;
    @Autowired private EnvironmentRepository environments;
    @Autowired private ComponentRepository components;
    @Autowired private SystemRepository systems;
    @Autowired private EnvironmentComponentVersionStateRepository snapshots;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentFirstSuccessUpdatesCreateOnlyOneSnapshotWithoutStaging() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        String[] externalIds = tx.execute(status -> {
            Environment environment = environments.save(new Environment("REF"));
            var component = components.save(new ch.admin.bit.jeap.deploymentlog.domain.Component(
                    "service", systems.save(new ch.admin.bit.jeap.deploymentlog.domain.System("SYS"))));
            Deployment first = TestDataFactory.createDeployment(environment, component, ZonedDateTime.now(),
                    "1.0", TestDataFactory.createDeploymentTarget());
            Deployment second = TestDataFactory.createDeployment(environment, component, ZonedDateTime.now(),
                    "2.0", TestDataFactory.createDeploymentTarget());
            first.getDeploymentTypes().add(DeploymentType.CODE);
            second.getDeploymentTypes().add(DeploymentType.CODE);
            deployments.save(first);
            deployments.save(second);
            return new String[]{first.getExternalId(), second.getExternalId()};
        });
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondRowLocked = new CountDownLatch(1);
        DeploymentRepository observedDeployments = mock(DeploymentRepository.class, delegatesTo(deployments));
        doAnswer(invocation -> {
            String externalId = invocation.getArgument(0);
            var result = deployments.findByExternalIdForUpdate(externalId);
            if (externalId.equals(externalIds[1])) secondRowLocked.countDown();
            return result;
        }).when(observedDeployments).findByExternalIdForUpdate(anyString());
        DeploymentStagingService staging = mock(DeploymentStagingService.class);
        var service = new DeploymentService(observedDeployments, mock(DeploymentPageRepository.class), systems,
                environments, mock(SystemService.class), components, snapshots, staging, mock(ApplicationEventPublisher.class));

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                succeed(service, externalIds[0]);
                firstUpdated.countDown();
                await(releaseFirst);
            }));
            try {
                assertThat(firstUpdated.await(10, TimeUnit.SECONDS)).isTrue();
                Future<?> second = pool.submit(() -> tx.executeWithoutResult(status -> succeed(service, externalIds[1])));
                assertThat(secondRowLocked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }
        }
        tx.executeWithoutResult(status -> {
            Deployment second = deployments.findByExternalId(externalIds[1]).orElseThrow();
            var component = second.getComponentVersion().getComponent();
            assertThat(snapshots.findByComponentIn(Set.of(component))).hasSize(1);
            var snapshot = snapshots.findByEnvironmentAndComponent(second.getEnvironment(), component).orElseThrow();
            assertThat(snapshot.getDeployment().getId()).isEqualTo(second.getId());
        });
        verifyNoInteractions(staging);
    }

    private static void succeed(DeploymentService service, String externalId) {
        try {
            service.updateState(externalId, DeploymentState.SUCCESS, "done", ZonedDateTime.now(), Map.of());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for commit");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
