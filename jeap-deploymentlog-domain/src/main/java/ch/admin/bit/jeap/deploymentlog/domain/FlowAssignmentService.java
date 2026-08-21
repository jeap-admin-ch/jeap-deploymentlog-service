package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.AmbiguousOpenFlowException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Transactional
public class FlowAssignmentService {

    private final FlowRepository flowRepository;
    private final FlowTypeClassifier flowTypeClassifier;

    public Optional<Flow> assign(Deployment deployment, Environment finalDeploymentEnvironment) {
        if (!isFlowRelevant(deployment)) {
            return Optional.empty();
        }

        Optional<Flow> assignedFlow = flowRepository.findByDeploymentId(deployment.getId());
        if (assignedFlow.isPresent()) {
            return assignedFlow;
        }

        ComponentVersion componentVersion = deployment.getComponentVersion();
        Component component = componentVersion.getComponent();
        String versionName = componentVersion.getVersionName();

        // A database lock, rather than an in-process mutex, serializes creation across service instances.
        flowRepository.lockComponent(component.getId());

        List<Flow> openFlows = flowRepository.findOpenFlows(component.getId(), versionName);
        if (openFlows.size() > 1) {
            throw new AmbiguousOpenFlowException(component.getId(), versionName, openFlows.size());
        }
        Flow flow;
        if (openFlows.size() == 1) {
            flow = openFlows.getFirst();
            flow.add(deployment);
        } else {
            FlowType type = flowTypeClassifier.classify(deployment);
            flow = flowRepository.save(Flow.start(type, deployment, finalDeploymentEnvironment));
        }
        return Optional.of(flow);
    }

    public boolean isFlowRelevant(Deployment deployment) {
        return deployment.getSequence() != DeploymentSequence.UNDEPLOYED
                && deployment.getDeploymentTypes() != null
                && deployment.getDeploymentTypes().contains(DeploymentType.CODE);
    }
}
