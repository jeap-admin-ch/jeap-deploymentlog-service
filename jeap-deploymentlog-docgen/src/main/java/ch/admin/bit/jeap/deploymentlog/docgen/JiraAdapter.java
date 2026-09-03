package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class JiraAdapter {

    private final JiraWebClient jiraWebClient;
    private final Counter remoteLinkErrorCounter;

    public JiraAdapter(JiraWebClient jiraWebClient, MeterRegistry meterRegistry) {
        this.jiraWebClient = jiraWebClient;
        this.remoteLinkErrorCounter = meterRegistry.counter("deploymentlog.docgen.jiraissuelink.error");
    }

    public void updateIssuePageRemoteLink(String jiraIssueKey, String pageId) {
        try {
            jiraWebClient.upsertDeploymentLogIssuePageRemoteLink(jiraIssueKey, pageId);
        } catch (Exception ex) {
            remoteLinkErrorCounter.increment();
            log.warn("Failed to update stable DeploymentLog issue page link for Jira issue {}",
                    value("jiraIssueKey", jiraIssueKey), ex);
        }
    }
}
