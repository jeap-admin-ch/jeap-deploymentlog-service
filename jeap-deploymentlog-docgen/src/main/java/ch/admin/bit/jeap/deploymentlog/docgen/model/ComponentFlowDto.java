package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ComponentFlowDto {
    String flowId;
    String version;
    String versionControlUrl;
    String bornAt;
    String duration;
    String type;
    String state;
    String targetStage;
    List<ComponentFlowDeploymentDto> deployments;
    List<JiraIssueDto> jiraIssues;
    String evaluation;
}
