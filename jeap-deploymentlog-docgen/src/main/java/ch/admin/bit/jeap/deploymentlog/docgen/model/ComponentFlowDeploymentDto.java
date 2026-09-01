package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ComponentFlowDeploymentDto {
    String startedAt;
    String stage;
    String state;
    String pageUrl;
}
