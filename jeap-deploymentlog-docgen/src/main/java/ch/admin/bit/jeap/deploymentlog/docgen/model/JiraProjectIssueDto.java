package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JiraProjectIssueDto {
    String issueKey;
    String jiraIssueUrl;
    String deploymentLogIssuePageUrl;
    String latestDeploymentAt;
    String highestSuccessfulStage;
    String failedHigherStage;
    boolean successfullyDeployedToProduction;
}
