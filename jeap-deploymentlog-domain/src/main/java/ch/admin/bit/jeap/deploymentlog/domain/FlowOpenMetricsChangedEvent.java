package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record FlowOpenMetricsChangedEvent(UUID flowId, String system, String component, FlowType type, boolean open) {

    public static FlowOpenMetricsChangedEvent opened(Flow flow) {
        return from(flow, true);
    }

    public static FlowOpenMetricsChangedEvent noLongerOpen(Flow flow) {
        return from(flow, false);
    }

    private static FlowOpenMetricsChangedEvent from(Flow flow, boolean open) {
        Component component = flow.getComponentVersion().getComponent();
        return new FlowOpenMetricsChangedEvent(
                flow.getId(), component.getSystem().getName(), component.getName(), flow.getType(), open);
    }
}
