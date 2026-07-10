package ch.admin.bit.jeap.deploymentlog.jira;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

/**
 * Thrown when jira cannot be used at all: jira is not reachable, replies with a server error even after
 * retries, replies with an unusable response, or rejects the requests of the deployment log service itself
 * (e.g. invalid credentials). This always indicates a problem with jira or the jira configuration of the
 * deployment log service, never a problem with the jira issues that were looked up.
 */
public class JiraUnavailableException extends RuntimeException {

    private JiraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public static JiraUnavailableException jiraNotAvailable(RestClientException cause) {
        return new JiraUnavailableException(
                "Jira is not available - the ready-for-deploy check could not be executed", cause);
    }

    public static JiraUnavailableException jiraClientError(String action, HttpClientErrorException cause) {
        String message = ("Jira request failed when %s: HTTP %s. This could indicate a problem with the jira " +
                "configuration of the deployment log service (e.g. invalid jira credentials). Jira response body: %s")
                .formatted(action, cause.getStatusCode(), cause.getResponseBodyAsString(StandardCharsets.UTF_8));
        return new JiraUnavailableException(message, cause);
    }

    public static JiraUnavailableException emptyJiraResponse(String action) {
        return new JiraUnavailableException(
                ("Jira returned an empty or unparseable response when %s - the ready-for-deploy check " +
                        "could not be executed").formatted(action), null);
    }
}
