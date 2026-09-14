package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.Getter;
import org.springframework.web.client.RestClientException;

@Getter
public class JiraIssueNotFoundException extends RuntimeException {

    private final String issueKey;

    public JiraIssueNotFoundException(String issueKey, RestClientException cause) {
        super("Jira issue '%s' does not exist or is not visible".formatted(issueKey), cause);
        this.issueKey = issueKey;
    }
}
