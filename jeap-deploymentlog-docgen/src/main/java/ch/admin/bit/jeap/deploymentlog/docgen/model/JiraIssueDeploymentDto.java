package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JiraIssueDeploymentDto {
    String startedAt;
    String stage;
    String system;
    String component;
    String version;
    String deploymentTypes;
    String state;
    String deploymentPageUrl;
    String startedBy;
}
