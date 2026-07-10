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
     * Labels by issue key for all requested jira issues that were found in jira.
     */
    Map<String, List<String>> labelsByIssueKey;

    /**
     * Requested issue keys that could not be resolved in jira: the issue does not exist, is not readable
     * for the deployment log jira user, or the key is not even a syntactically valid jira issue key.
     */
    Set<String> notFoundIssueKeys;
}
