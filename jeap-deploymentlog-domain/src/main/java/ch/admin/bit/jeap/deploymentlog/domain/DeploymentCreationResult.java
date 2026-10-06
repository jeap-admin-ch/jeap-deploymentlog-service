package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record DeploymentCreationResult(UUID deploymentId, boolean created) {
}
