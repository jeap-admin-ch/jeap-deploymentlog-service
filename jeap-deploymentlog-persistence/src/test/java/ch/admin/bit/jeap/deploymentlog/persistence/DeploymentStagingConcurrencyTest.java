package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.*;
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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentStagingConcurrencyTest {
    @Autowired VersionDeploymentRepository versions;
    @Autowired DeploymentRepository deployments;
    @Autowired EnvironmentRepository environments;
    @Autowired SystemRepository systems;
    @Autowired ComponentRepository components;
    @Autowired PlatformTransactionManager manager;
    @Autowired FlowStageResolver resolver;

    @Test void concurrentAttemptsAreClassifiedInOrderUnderComponentLock() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager);
        UUID[] ids = tx.execute(status -> {
            Environment env = new Environment("REF");
            env.setDevelopment(true);
            environments.save(env);
            environments.save(new Environment("PROD"));
            var system = systems.save(new ch.admin.bit.jeap.deploymentlog.domain.System("SYSTEM"));
            var component = components.save(new ch.admin.bit.jeap.deploymentlog.domain.Component("service", system));
            return new UUID[]{component.getId(), env.getId()};
        });
        var service = new DeploymentStagingService(versions, new FlowStageProperties(), resolver);
        var classifications = new ConcurrentLinkedQueue<DeploymentStagingType>();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> createDeployment = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                tx.executeWithoutResult(status -> {
                    versions.lockComponent(ids[0]);
                    var component = components.findById(ids[0]).orElseThrow();
                    var env = environments.findByName("REF").orElseThrow();
                    var d = TestDataFactory.createDeployment(env, component, ZonedDateTime.now(), "1.0",
                            ZonedDateTime.now(), TestDataFactory.createDeploymentTarget());
                    d.getDeploymentTypes().add(DeploymentType.CODE);
                    deployments.save(d);
                    service.prepare(d, java.util.List.of());
                    classifications.add(d.getStagingType());
                });
                return null;
            };
            Future<Void> one = pool.submit(createDeployment);
            Future<Void> two = pool.submit(createDeployment);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        assertThat(versions.history(ids[0])).hasSize(2);
        assertThat(classifications).containsExactly(DeploymentStagingType.NEW, DeploymentStagingType.RETRY);
    }
}
