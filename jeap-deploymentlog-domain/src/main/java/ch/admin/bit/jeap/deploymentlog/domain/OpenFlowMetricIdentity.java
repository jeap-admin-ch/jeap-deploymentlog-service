package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record OpenFlowMetricIdentity(UUID flowId, String system, String component, String finalEnvironment,
                                     FlowType type) {
}
