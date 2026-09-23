package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;

class DeploymentMetricEventPersistenceListenerTest {

    @Test
    void metricPersistenceFailureDoesNotEscapeAfterCommitListener() {
        DeploymentRepository repository = (DeploymentRepository) Proxy.newProxyInstance(
                DeploymentRepository.class.getClassLoader(),
                new Class<?>[]{DeploymentRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("recordTerminalDeploymentMetric")) {
                        throw new IllegalStateException("database unavailable");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        DeploymentTerminalMetricEvent event = new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "system", "component", "PROD",
                Set.of(DeploymentType.CODE), DeploymentState.SUCCESS,
                ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now());
        DeploymentMetricEventPersistenceListener listener =
                new DeploymentMetricEventPersistenceListener(repository);

        assertThatNoException().isThrownBy(() -> listener.recordTerminalDeploymentMetric(event));
    }
}
