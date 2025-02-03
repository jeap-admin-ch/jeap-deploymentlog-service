package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.jira.JiraIssuesNotFoundException;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentCheckService;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResult;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentCheckServiceTest {

    @Mock
    private JiraWebClient jiraWebClient;

    @InjectMocks
    private DeploymentCheckService service;

    @Captor
    ArgumentCaptor<Set<String>> jiraIssuesCaptor;

    @Test
    void issuesReadyForDeploy_whenAllWithLabel_thenReturnOk() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(Map.of("JEAP-1234", List.of(DeploymentCheckService.R4DEPLOY_LABEL)));

        //when
        final DeploymentCheckResultDto deploymentCheckResultDto = service.issuesReadyForDeploy(issues);

        //then
        assertThat(deploymentCheckResultDto.getResult()).isEqualTo(DeploymentCheckResult.OK);
        assertThat(deploymentCheckResultDto.getMessage()).isNull();
        verify(jiraWebClient, times(1)).searchIssuesLabels(issues);
    }

    @Test
    void issuesReadyForDeploy_withoutLabel_thenReturnsNok() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(Map.of("JEAP-1234", List.of()));

        //when
        final DeploymentCheckResultDto deploymentCheckResultDto = service.issuesReadyForDeploy(issues);

        //then
        assertThat(deploymentCheckResultDto.getResult()).isEqualTo(DeploymentCheckResult.NOK);
        assertThat(deploymentCheckResultDto.getMessage()).isEqualTo("Issues without label: [JEAP-1234]. Issues not found: []");
        verify(jiraWebClient, times(1)).searchIssuesLabels(issues);
    }

    @Test
    void issuesReadyForDeploy_withIssuesNotFound_thenReturnsWarning() {
        //given
        final Set<String> issues = new HashSet<>(Set.of("JEAP-1234", "JEAP-2345", "JEAP-4567"));
        when(jiraWebClient.searchIssuesLabels(issues))
                //first call
                .thenThrow(new JiraIssuesNotFoundException(List.of("JEAP-1234", "JEAP-4567")))
                //second call
                .thenReturn(Map.of("JEAP-2345", List.of(DeploymentCheckService.R4DEPLOY_LABEL)));

        //when
        final DeploymentCheckResultDto deploymentCheckResultDto = service.issuesReadyForDeploy(issues);

        //then
        assertThat(deploymentCheckResultDto.getResult()).isEqualTo(DeploymentCheckResult.WARNING);
        assertThat(deploymentCheckResultDto.getMessage()).isEqualTo("Issues not found: [JEAP-1234, JEAP-4567]");
        verify(jiraWebClient, times(2)).searchIssuesLabels(jiraIssuesCaptor.capture());
        final List<Set<String>> allValues = jiraIssuesCaptor.getAllValues();
        assertThat(allValues.get(0)).isEqualTo(issues);
        assertThat(allValues.get(1)).isEqualTo(Set.of("JEAP-2345"));
    }

    @Test
    void issuesReadyForDeploy_withIssuesNotFoundAndWithoutLabel_thenReturnNok() {
        //given
        final Set<String> issues = new HashSet<>(Set.of("JEAP-1234", "JEAP-2345", "JEAP-4567"));
        when(jiraWebClient.searchIssuesLabels(issues))
                //first call
                .thenThrow(new JiraIssuesNotFoundException(List.of("JEAP-1234", "JEAP-4567")))
                //second call
                .thenReturn(Map.of("JEAP-2345", List.of()));

        //when
        final DeploymentCheckResultDto deploymentCheckResultDto = service.issuesReadyForDeploy(issues);

        //then
        assertThat(deploymentCheckResultDto.getResult()).isEqualTo(DeploymentCheckResult.NOK);
        assertThat(deploymentCheckResultDto.getMessage()).isEqualTo("Issues without label: [JEAP-2345]. Issues not found: [JEAP-1234, JEAP-4567]");
        verify(jiraWebClient, times(2)).searchIssuesLabels(jiraIssuesCaptor.capture());
        final List<Set<String>> allValues = jiraIssuesCaptor.getAllValues();
        assertThat(allValues.get(0)).isEqualTo(issues);
        assertThat(allValues.get(1)).isEqualTo(Set.of("JEAP-2345"));
    }

    @Test
    void issuesReadyForDeploy_webClientCallFailed_thenThrowsException() {
        //given
        final Set<String> issues = new HashSet<>(Set.of("JEAP-1234", "JEAP-2345", "JEAP-4567"));
        when(jiraWebClient.searchIssuesLabels(issues))
                .thenThrow(new RestClientResponseException("my error message", 500, "status", null, null, null));

        //when
        assertThrows(RestClientResponseException.class, () -> {
            service.issuesReadyForDeploy(issues);
        });

        //then
        verify(jiraWebClient, times(1)).searchIssuesLabels(anySet());
    }

}
