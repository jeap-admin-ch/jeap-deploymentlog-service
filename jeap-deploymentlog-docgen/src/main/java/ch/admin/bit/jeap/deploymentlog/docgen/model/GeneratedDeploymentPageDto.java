package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class GeneratedDeploymentPageDto {

    DeploymentLetterPageDto deploymentLetterPageDto;
    String pageId;
    /** Jira projects whose change view has a pending refresh request for this deployment. */
    @Builder.Default
    Set<String> jiraProjectKeys = Set.of();
}
