package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public record DeploymentTerminalMetricEvent(
        UUID deploymentId,
        String externalId,
        String system,
        String component,
        String environment,
        DeploymentState state,
        ZonedDateTime startedAt,
        ZonedDateTime endedAt) {

    static DeploymentTerminalMetricEvent from(Deployment deployment) {
        Component component = deployment.getComponentVersion().getComponent();
        return new DeploymentTerminalMetricEvent(
                deployment.getId(),
                deployment.getExternalId(),
                component.getSystem().getName(),
                component.getName(),
                deployment.getEnvironment().getName(),
                deployment.getState(),
                deployment.getStartedAt(),
                deployment.getEndedAt());
    }
}
