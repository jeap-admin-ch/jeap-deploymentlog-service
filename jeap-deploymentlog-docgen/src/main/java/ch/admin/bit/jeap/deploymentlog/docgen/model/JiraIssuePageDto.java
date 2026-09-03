package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class JiraIssuePageDto {
    String issueKey;
    String jiraIssueUrl;
    List<JiraIssueDeploymentDto> deployments;
}
