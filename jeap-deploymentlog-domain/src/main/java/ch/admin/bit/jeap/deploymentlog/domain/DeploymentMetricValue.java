package ch.admin.bit.jeap.deploymentlog.domain;

public record DeploymentMetricValue(String system,
                                    String component,
                                    String environment,
                                    DeploymentType deploymentType,
                                    DeploymentState state,
                                    long value) {
}
