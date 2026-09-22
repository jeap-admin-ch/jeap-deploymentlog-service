package ch.admin.bit.jeap.deploymentlog.domain;

public record FlowMetricIdentity(String system,
                                 String component,
                                 String finalEnvironment,
                                 FlowType type) {
}
