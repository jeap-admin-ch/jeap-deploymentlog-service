package ch.admin.bit.jeap.deploymentlog.domain;

public record DeploymentMetricIdentity(String system,
                                       String component,
                                       String environment,
                                       DeploymentType deploymentType) {
}
