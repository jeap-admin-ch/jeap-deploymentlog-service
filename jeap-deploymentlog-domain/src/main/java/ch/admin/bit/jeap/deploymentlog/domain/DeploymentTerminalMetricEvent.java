package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

public record DeploymentTerminalMetricEvent(
        UUID deploymentId,
        String externalId,
        String system,
        String component,
        String environment,
        Set<DeploymentType> deploymentTypes,
        DeploymentState state,
        ZonedDateTime startedAt,
        ZonedDateTime endedAt,
        String stagingVersion) {

    // Retain the existing constructor for callers that do not supply a staging version.
    @SuppressWarnings("java:S107")
    public DeploymentTerminalMetricEvent(UUID deploymentId, String externalId, String system, String component,
                                         String environment, Set<DeploymentType> deploymentTypes, DeploymentState state,
                                         ZonedDateTime startedAt, ZonedDateTime endedAt) {
        this(deploymentId, externalId, system, component, environment, deploymentTypes, state, startedAt, endedAt, null);
    }

    static DeploymentTerminalMetricEvent from(Deployment deployment) {
        Component component = deployment.getComponentVersion().getComponent();
        return new DeploymentTerminalMetricEvent(
                deployment.getId(),
                deployment.getExternalId(),
                component.getSystem().getName(),
                component.getName(),
                deployment.getEnvironment().getName(),
                Set.copyOf(deployment.getDeploymentTypes()),
                deployment.getState(),
                deployment.getStartedAt(),
                deployment.getEndedAt(),
                deployment.getState() == DeploymentState.SUCCESS
                        && deployment.getSequence() != DeploymentSequence.UNDEPLOYED
                        && deployment.getDeploymentTypes().contains(DeploymentType.CODE)
                        ? deployment.getComponentVersion().getVersionName() : null);
    }
}
