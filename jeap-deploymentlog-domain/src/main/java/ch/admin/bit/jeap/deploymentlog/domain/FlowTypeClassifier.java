package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class FlowTypeClassifier {

    private final EnvironmentComponentVersionStateRepository environmentStateRepository;
    private final DeploymentRepository deploymentRepository;
    private final FlowStageResolver flowStageResolver;

    public FlowType classify(Deployment deployment) {
        ComponentVersion componentVersion = deployment.getComponentVersion();
        Component component = componentVersion.getComponent();
        Environment environment = deployment.getEnvironment();
        String versionName = componentVersion.getVersionName();

        boolean currentlyDeployed = environmentStateRepository
                .findByEnvironmentAndComponent(environment, component)
                .map(EnvironmentComponentVersionState::getComponentVersion)
                .map(ComponentVersion::getVersionName)
                .filter(versionName::equals)
                .isPresent();
        if (currentlyDeployed) {
            return FlowType.RETRY;
        }

        Optional<Deployment> lastDeployment = deploymentRepository
                .getLastDeploymentForBusinessVersion(component, environment, versionName, deployment.getId());
        if (lastDeployment.map(Deployment::getState).filter(DeploymentState.FAILURE::equals).isPresent()) {
            return FlowType.RETRY;
        }

        if (deploymentRepository.hasSuccessfulDeploymentForBusinessVersion(component, environment, versionName,
                deployment.getId())) {
            return FlowType.ROLLBACK;
        }

        Environment startEnvironment = flowStageResolver.resolveStartEnvironment();
        if (startEnvironment.getId().equals(environment.getId())) {
            return FlowType.NEW;
        }
        return FlowType.AD_HOC;
    }
}
