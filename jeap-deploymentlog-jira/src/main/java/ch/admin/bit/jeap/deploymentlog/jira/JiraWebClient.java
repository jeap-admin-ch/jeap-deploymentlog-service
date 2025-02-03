package ch.admin.bit.jeap.deploymentlog.jira;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Retryable(value = RestClientException.class,
        maxAttempts = 4,
        backoff = @Backoff(delay = 2000, multiplier = 2))
public interface JiraWebClient {

    void updateIssueWithConfluenceLink(String jiraIssueKey, String pageId);

    Map<String, List<String>> searchIssuesLabels(Set<String> jiraIssueKeys) throws JiraIssuesNotFoundException;

}
