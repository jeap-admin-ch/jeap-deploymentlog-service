package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Set;

public record DeploymentStartedMetricEvent(String system,
                                           String component,
                                           String environment,
                                           Set<DeploymentType> deploymentTypes) {

    public DeploymentStartedMetricEvent {
        deploymentTypes = deploymentTypes == null ? Set.of() : Set.copyOf(deploymentTypes);
    }

    static DeploymentStartedMetricEvent from(Deployment deployment) {
        Component component = deployment.getComponentVersion().getComponent();
        return new DeploymentStartedMetricEvent(
                component.getSystem().getName(),
                component.getName(),
                deployment.getEnvironment().getName(),
                deployment.getDeploymentTypes());
    }
}
