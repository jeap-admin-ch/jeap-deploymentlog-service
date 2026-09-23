package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
class DeploymentMetricEventPersistenceListener {

    private final DeploymentRepository deploymentRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void recordTerminalDeploymentMetric(DeploymentTerminalMetricEvent event) {
        try {
            deploymentRepository.recordTerminalDeploymentMetric(event);
        } catch (RuntimeException ex) {
            log.warn("Could not persist the terminal deployment metric for deployment {}; " +
                            "the metrics reconciliation job will retry it",
                    event.deploymentId(), ex);
        }
    }
}
