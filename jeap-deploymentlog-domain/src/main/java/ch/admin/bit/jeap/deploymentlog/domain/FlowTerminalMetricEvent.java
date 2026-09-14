package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public record FlowTerminalMetricEvent(
        UUID flowId,
        String system,
        String component,
        String finalEnvironment,
        FlowType type,
        FlowState state,
        ZonedDateTime bornAt,
        ZonedDateTime endedAt) {

    static FlowTerminalMetricEvent closed(Flow flow, Deployment closingDeployment) {
        return from(flow, closingDeployment.getEndedAt());
    }

    static FlowTerminalMetricEvent aborted(Flow flow) {
        return from(flow, null);
    }

    private static FlowTerminalMetricEvent from(Flow flow, ZonedDateTime endedAt) {
        Component component = flow.getComponentVersion().getComponent();
        return new FlowTerminalMetricEvent(
                flow.getId(),
                component.getSystem().getName(),
                component.getName(),
                flow.getFinalDeploymentEnvironment().getName(),
                flow.getType(),
                flow.getState(),
                flow.getBornAt(),
                endedAt);
    }
}
