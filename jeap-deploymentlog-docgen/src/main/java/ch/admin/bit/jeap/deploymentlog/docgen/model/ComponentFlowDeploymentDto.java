package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ComponentFlowDeploymentDto {
    String startedAt;
    Instant startedAtInstant;
    String stage;
    String state;
    String type;
    int stagingOrder;
    java.util.List<String> finalDeploymentEnvironments;
    String pageUrl;
}
