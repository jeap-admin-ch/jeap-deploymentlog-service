package ch.admin.bit.jeap.deploymentlog.jira;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClientException;

import java.util.Set;

// noRetryFor is required because JiraUnavailableException wraps a RestClientException as cause,
// which the retry classifier would otherwise pick up via cause traversal
@Retryable(retryFor = RestClientException.class,
        noRetryFor = JiraUnavailableException.class,
        maxAttempts = 4,
        backoff = @Backoff(delayExpression = "${jeap.deploymentlog.jira.retry-delay-ms:2000}", multiplier = 2))
public interface JiraWebClient {

    void updateIssueWithConfluenceLink(String jiraIssueKey, String pageId);

    /**
     * Search the labels of the given jira issues. Issue keys that cannot be resolved in jira (issue does not
     * exist, is not readable, or the key is syntactically invalid) never fail the search - they are reported
     * in {@link JiraIssuesSearchResult#getNotFoundIssueKeys()}.
     *
     * @throws JiraUnavailableException if jira rejects the request of the deployment log service itself
     *                                  (e.g. invalid jira credentials)
     * @throws RestClientException     if jira is not reachable or replies with a server error (retried)
     */
    JiraIssuesSearchResult searchIssuesLabels(Set<String> jiraIssueKeys);

    /**
     * @return the keys of all jira projects visible to (browsable by) the deployment log jira user
     */
    Set<String> getVisibleProjectKeys();
}
