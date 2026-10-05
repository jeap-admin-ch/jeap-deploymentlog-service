package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class JiraIssuesSearchResult {

    /**
     * Labels by issue key for all requested jira issues that were found in jira and are not exempt from the
     * label check.
     */
    Map<String, List<String>> labelsByIssueKey;

    /**
     * Keys of the requested jira issues that were found in jira and are exempt from the label check (sorted),
     * e.g. issues of a configured exempt issue type (`Epic` by default). Their label is not checked and they
     * are therefore not contained in {@link #labelsByIssueKey}.
     */
    @Builder.Default
    List<String> ignoredIssueKeys = List.of();

    /**
     * Requested issue keys that could not be resolved in jira: the issue does not exist, is not readable
     * for the deployment log jira user, or the key is not even a syntactically valid jira issue key.
     */
    Set<String> notFoundIssueKeys;
}
