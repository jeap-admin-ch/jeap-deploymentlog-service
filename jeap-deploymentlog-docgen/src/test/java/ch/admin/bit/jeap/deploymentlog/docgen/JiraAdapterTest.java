package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.jira.JiraIssueNotFoundException;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraAdapterTest {

    @Mock
    JiraWebClient jiraWebClient;

    JiraAdapter jiraAdapter;

    @Mock
    MeterRegistry meterRegistry;
    @Mock
    Counter counter;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("deploymentlog.docgen.jiraissuelink.error")).thenReturn(counter);
        jiraAdapter = new JiraAdapter(jiraWebClient, meterRegistry);
    }

    @Test
    void jiraFailureIsIgnoredAndMeasured() {
        doThrow(mock(RestClientException.class)).when(jiraWebClient)
                .upsertDeploymentLogIssuePageRemoteLink(anyString(), anyString());

        assertThat(jiraAdapter.updateIssuePageRemoteLink("JEAP-1234", "pageId")).isFalse();

        verify(counter).increment();
    }

    @Test
    void missingJiraIssueIsIgnoredWithoutCountingAnIntegrationFailure() {
        doThrow(new JiraIssueNotFoundException("CVE-2026", mock(RestClientException.class)))
                .when(jiraWebClient).upsertDeploymentLogIssuePageRemoteLink(anyString(), anyString());

        assertThat(jiraAdapter.updateIssuePageRemoteLink("CVE-2026", "pageId")).isFalse();

        verify(counter, never()).increment();
    }

    @Test
    void acceptedLinkIsReported() {
        assertThat(jiraAdapter.updateIssuePageRemoteLink("JEAP-1234", "pageId")).isTrue();
    }

}
