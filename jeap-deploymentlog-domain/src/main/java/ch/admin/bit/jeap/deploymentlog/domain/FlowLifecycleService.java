package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Transactional
public class FlowLifecycleService {

    private final FlowRepository flowRepository;
    private final FlowStageProperties flowStageProperties;

    public void process(Deployment deployment) {
        if (!flowStageProperties.isEnabled() || !isSuccessfulCodeDeployment(deployment)) {
            return;
        }

        flowRepository.findByDeploymentIdAndLockComponent(deployment.getId()).ifPresent(flow -> {
            Component component = flow.getComponentVersion().getComponent();
            flow.closeIfTargetReached(deployment);
            if (deployment.getEnvironment().isProductive()) {
                abortOlderOpenFlows(flow, component);
            }
        });
    }

    private void abortOlderOpenFlows(Flow abortingFlow, Component component) {
        ZonedDateTime committedAt = abortingFlow.getComponentVersion().getCommittedAt();
        flowRepository.findOlderOpenFlows(component.getId(), committedAt, abortingFlow.getId())
                .forEach(flow -> flow.abortBy(abortingFlow));
    }

    private boolean isSuccessfulCodeDeployment(Deployment deployment) {
        return deployment.getState() == DeploymentState.SUCCESS
                && deployment.getSequence() != DeploymentSequence.UNDEPLOYED
                && deployment.getDeploymentTypes() != null
                && deployment.getDeploymentTypes().contains(DeploymentType.CODE);
    }
}
