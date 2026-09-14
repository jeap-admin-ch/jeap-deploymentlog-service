package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Transactional
public class FlowLifecycleService {

    private final FlowRepository flowRepository;
    private final FlowStageProperties flowStageProperties;
    private final ApplicationEventPublisher eventPublisher;

    public void process(Deployment deployment) {
        if (!flowStageProperties.isEnabled() || !isSuccessfulCodeDeployment(deployment)) {
            return;
        }

        flowRepository.findByDeploymentIdAndLockComponent(deployment.getId()).ifPresent(flow -> {
            Component component = flow.getComponentVersion().getComponent();
            if (flow.closeIfTargetReached(deployment)) {
                eventPublisher.publishEvent(FlowTerminalMetricEvent.closed(flow, deployment));
                eventPublisher.publishEvent(FlowOpenMetricsChangedEvent.noLongerOpen(flow));
            }
            if (deployment.getEnvironment().isProductive()) {
                abortOlderOpenFlows(flow, component);
            }
        });
    }

    private void abortOlderOpenFlows(Flow abortingFlow, Component component) {
        ZonedDateTime committedAt = abortingFlow.getComponentVersion().getCommittedAt();
        for (Flow flow : flowRepository.findOlderOpenFlows(component.getId(), committedAt, abortingFlow.getId())) {
            if (flow.abortBy(abortingFlow)) {
                eventPublisher.publishEvent(FlowTerminalMetricEvent.aborted(flow));
                eventPublisher.publishEvent(FlowOpenMetricsChangedEvent.noLongerOpen(flow));
            }
        }
    }

    private boolean isSuccessfulCodeDeployment(Deployment deployment) {
        return deployment.getState() == DeploymentState.SUCCESS
                && deployment.getSequence() != DeploymentSequence.UNDEPLOYED
                && deployment.getDeploymentTypes() != null
                && deployment.getDeploymentTypes().contains(DeploymentType.CODE);
    }
}
