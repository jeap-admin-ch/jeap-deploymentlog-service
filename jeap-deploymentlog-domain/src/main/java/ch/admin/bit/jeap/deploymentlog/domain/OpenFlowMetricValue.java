package ch.admin.bit.jeap.deploymentlog.domain;

public record OpenFlowMetricValue(String system, String component, FlowType type, long count) {
}
