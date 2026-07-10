package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.jira.JiraIssuesSearchResult;
import ch.admin.bit.jeap.deploymentlog.jira.JiraUnavailableException;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResult;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentCheckService {

    public static final String R4DEPLOY_LABEL = "R4DEPLOY";

    private final JiraWebClient jiraWebClient;

    public DeploymentCheckResultDto checkIssuesReadyForDeploy(Set<String> issues) {
        log.debug("Check if the issues '{}' are ready to be deployed", issues);

        JiraIssuesSearchResult searchResult = searchIssuesLabels(issues);

        List<String> issuesWithoutLabel = searchResult.getLabelsByIssueKey().entrySet().stream()
                .filter(entry -> !entry.getValue().contains(R4DEPLOY_LABEL))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        UnresolvedIssues unresolvedIssues = categorizeUnresolvedIssues(searchResult.getNotFoundIssueKeys());

        return generateResultDto(issuesWithoutLabel, unresolvedIssues);
    }

    private JiraIssuesSearchResult searchIssuesLabels(Set<String> issues) {
        try {
            return jiraWebClient.searchIssuesLabels(issues);
        } catch (RestClientException ex) {
            throw JiraUnavailableException.jiraNotAvailable(ex);
        }
    }

    /**
     * Jira reports "no permission to browse the project" and "issue/project does not exist" identically
     * (anti-enumeration). To give the pipeline user an actionable hint, unresolved issues are categorized
     * by whether their project is visible to the deployment log jira user:
     * project visible -> the individual issue does not exist (or is not readable),
     * project not visible -> either the browse permission is missing on the project or the key is not a
     * jira issue reference at all (e.g. accidentally extracted from a commit message).
     */
    private UnresolvedIssues categorizeUnresolvedIssues(Set<String> notFoundIssueKeys) {
        if (notFoundIssueKeys.isEmpty()) {
            return UnresolvedIssues.NONE;
        }

        Set<String> visibleProjectKeys = getVisibleProjectKeys();
        List<String> issuesNotFoundInVisibleProjects = new ArrayList<>();
        List<String> issuesInNotVisibleProjects = new ArrayList<>();
        SortedSet<String> projectsNotVisible = new TreeSet<>();
        notFoundIssueKeys.stream().sorted().forEach(issueKey -> {
            String projectKey = projectKeyOf(issueKey);
            if (visibleProjectKeys.contains(projectKey)) {
                issuesNotFoundInVisibleProjects.add(issueKey);
            } else {
                issuesInNotVisibleProjects.add(issueKey);
                projectsNotVisible.add(projectKey);
            }
        });
        return new UnresolvedIssues(issuesNotFoundInVisibleProjects, issuesInNotVisibleProjects, List.copyOf(projectsNotVisible));
    }

    private Set<String> getVisibleProjectKeys() {
        try {
            return jiraWebClient.getVisibleProjectKeys();
        } catch (RestClientException ex) {
            throw JiraUnavailableException.jiraNotAvailable(ex);
        }
    }

    private static String projectKeyOf(String issueKey) {
        int dashIndex = issueKey.indexOf('-');
        return dashIndex > 0 ? issueKey.substring(0, dashIndex) : issueKey;
    }

    private DeploymentCheckResultDto generateResultDto(List<String> issuesWithoutLabel, UnresolvedIssues unresolvedIssues) {
        if (unresolvedIssues.isNotEmpty()) {
            log.warn("""
                            Ready-for-deploy check could not resolve all jira issues. \
                            Issues not found in projects visible to the deployment log jira user: {}. \
                            Issues in projects not visible to the deployment log jira user: {} (projects: {}) \
                            - either the browse permission for the deployment log jira user is missing on the project, \
                            or the key is not a jira issue reference (e.g. from a badly created commit message).""",
                    unresolvedIssues.issuesNotFoundInVisibleProjects(),
                    unresolvedIssues.issuesInNotVisibleProjects(),
                    unresolvedIssues.projectsNotVisible());
        }

        DeploymentCheckResult result;
        if (!issuesWithoutLabel.isEmpty()) {
            result = DeploymentCheckResult.NOK;
        } else if (unresolvedIssues.isNotEmpty()) {
            result = DeploymentCheckResult.WARNING;
        } else {
            result = DeploymentCheckResult.OK;
        }

        return DeploymentCheckResultDto.builder()
                .result(result)
                .message(buildMessage(issuesWithoutLabel, unresolvedIssues))
                .issuesWithoutLabel(issuesWithoutLabel)
                .issuesNotFound(unresolvedIssues.allIssues())
                .projectsNotVisible(unresolvedIssues.projectsNotVisible())
                .build();
    }

    private static String buildMessage(List<String> issuesWithoutLabel, UnresolvedIssues unresolvedIssues) {
        List<String> messageParts = new ArrayList<>();
        if (!issuesWithoutLabel.isEmpty()) {
            messageParts.add("Issues without label: " + issuesWithoutLabel + ".");
        }
        if (!unresolvedIssues.issuesNotFoundInVisibleProjects().isEmpty()) {
            messageParts.add("Issues not found in jira: " + unresolvedIssues.issuesNotFoundInVisibleProjects() + ".");
        }
        if (!unresolvedIssues.issuesInNotVisibleProjects().isEmpty()) {
            messageParts.add(("Issues in jira projects not visible to the deployment log service: %s " +
                    "(projects: %s) - either the jira project does not exist (e.g. the key was extracted " +
                    "from a badly created commit message) or the deployment log jira user is missing the " +
                    "browse permission on the project.")
                    .formatted(unresolvedIssues.issuesInNotVisibleProjects(), unresolvedIssues.projectsNotVisible()));
        }
        return messageParts.isEmpty() ? null : String.join(" ", messageParts);
    }

    private record UnresolvedIssues(List<String> issuesNotFoundInVisibleProjects,
                                    List<String> issuesInNotVisibleProjects,
                                    List<String> projectsNotVisible) {

        static final UnresolvedIssues NONE = new UnresolvedIssues(List.of(), List.of(), List.of());

        boolean isNotEmpty() {
            return !issuesNotFoundInVisibleProjects.isEmpty() || !issuesInNotVisibleProjects.isEmpty();
        }

        List<String> allIssues() {
            return Stream.concat(issuesNotFoundInVisibleProjects.stream(), issuesInNotVisibleProjects.stream())
                    .sorted()
                    .toList();
        }
    }
}
