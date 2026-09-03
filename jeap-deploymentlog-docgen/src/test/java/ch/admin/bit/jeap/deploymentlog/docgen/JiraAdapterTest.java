package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

        assertDoesNotThrow(() -> jiraAdapter.updateIssuePageRemoteLink("JEAP-1234", "pageId"));

        verify(counter).increment();
    }

}
