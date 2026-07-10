package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeploymentCheckResultDto {

    DeploymentCheckResult result;

    String message;

    /**
     * Issues that were found in jira but do not carry the ready-for-deploy label
     */
    @Builder.Default
    List<String> issuesWithoutLabel = List.of();

    /**
     * Issues that could not be resolved in jira: the issue does not exist, is not readable for the
     * deployment log jira user, or the key is not a syntactically valid jira issue key
     */
    @Builder.Default
    List<String> issuesNotFound = List.of();

    /**
     * Jira project keys referenced by commit messages that are not visible to the deployment log jira user:
     * either the project does not exist (e.g. the key was extracted from a commit message not starting with
     * a jira ticket reference) or the deployment log jira user is missing the browse permission on the project.
     */
    @Builder.Default
    List<String> projectsNotVisible = List.of();
}
