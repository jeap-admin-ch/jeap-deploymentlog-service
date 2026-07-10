package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.jira.JiraIssuesSearchResult;
import ch.admin.bit.jeap.deploymentlog.jira.JiraUnavailableException;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentCheckService;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResult;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.admin.bit.jeap.deploymentlog.web.api.DeploymentCheckService.R4DEPLOY_LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentCheckServiceTest {

    @Mock
    private JiraWebClient jiraWebClient;

    @InjectMocks
    private DeploymentCheckService service;

    @Test
    void checkIssuesReadyForDeploy_whenAllWithLabel_thenReturnsOk() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(searchResult(
                Map.of("JEAP-1234", List.of(R4DEPLOY_LABEL),
                        "JEAP-2345", List.of("otherLabel", R4DEPLOY_LABEL)),
                Set.of()));

        //when
        final DeploymentCheckResultDto result = service.checkIssuesReadyForDeploy(issues);

        //then
        assertThat(result.getResult()).isEqualTo(DeploymentCheckResult.OK);
        assertThat(result.getMessage()).isNull();
        assertThat(result.getIssuesWithoutLabel()).isEmpty();
        assertThat(result.getIssuesNotFound()).isEmpty();
        assertThat(result.getProjectsNotVisible()).isEmpty();
        verify(jiraWebClient, never()).getVisibleProjectKeys();
    }

    @Test
    void checkIssuesReadyForDeploy_withoutLabel_thenReturnsNok() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-2345");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(searchResult(
                Map.of("JEAP-1234", List.of(),
                        "JEAP-2345", List.of(R4DEPLOY_LABEL)),
                Set.of()));

        //when
        final DeploymentCheckResultDto result = service.checkIssuesReadyForDeploy(issues);

        //then
        assertThat(result.getResult()).isEqualTo(DeploymentCheckResult.NOK);
        assertThat(result.getMessage()).isEqualTo("Issues without label: [JEAP-1234].");
        assertThat(result.getIssuesWithoutLabel()).containsExactly("JEAP-1234");
        assertThat(result.getIssuesNotFound()).isEmpty();
        assertThat(result.getProjectsNotVisible()).isEmpty();
    }

    @Test
    void checkIssuesReadyForDeploy_withIssuesNotFoundInVisibleProject_thenReturnsWarning() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-9999");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(searchResult(
                Map.of("JEAP-1234", List.of(R4DEPLOY_LABEL)),
                Set.of("JEAP-9999")));
        when(jiraWebClient.getVisibleProjectKeys()).thenReturn(Set.of("JEAP"));

        //when
        final DeploymentCheckResultDto result = service.checkIssuesReadyForDeploy(issues);

        //then
        assertThat(result.getResult()).isEqualTo(DeploymentCheckResult.WARNING);
        assertThat(result.getMessage()).isEqualTo("Issues not found in jira: [JEAP-9999].");
        assertThat(result.getIssuesWithoutLabel()).isEmpty();
        assertThat(result.getIssuesNotFound()).containsExactly("JEAP-9999");
        assertThat(result.getProjectsNotVisible()).isEmpty();
    }

    @Test
    void checkIssuesReadyForDeploy_withIssuesInNotVisibleProjects_thenReturnsWarningNamingTheProjects() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "UTF-8", "AND-1");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(searchResult(
                Map.of("JEAP-1234", List.of(R4DEPLOY_LABEL)),
                Set.of("UTF-8", "AND-1")));
        when(jiraWebClient.getVisibleProjectKeys()).thenReturn(Set.of("JEAP"));

        //when
        final DeploymentCheckResultDto result = service.checkIssuesReadyForDeploy(issues);

        //then
        assertThat(result.getResult()).isEqualTo(DeploymentCheckResult.WARNING);
        assertThat(result.getMessage())
                .contains("Issues in jira projects not visible to the deployment log service: [AND-1, UTF-8]")
                .contains("(projects: [AND, UTF])")
                .contains("browse permission");
        assertThat(result.getIssuesWithoutLabel()).isEmpty();
        assertThat(result.getIssuesNotFound()).containsExactly("AND-1", "UTF-8");
        assertThat(result.getProjectsNotVisible()).containsExactly("AND", "UTF");
    }

    @Test
    void checkIssuesReadyForDeploy_withIssuesNotFoundAndWithoutLabel_thenReturnsNok() {
        //given
        final Set<String> issues = Set.of("JEAP-1234", "JEAP-9999", "UTF-8");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(searchResult(
                Map.of("JEAP-1234", List.of()),
                Set.of("JEAP-9999", "UTF-8")));
        when(jiraWebClient.getVisibleProjectKeys()).thenReturn(Set.of("JEAP"));

        //when
        final DeploymentCheckResultDto result = service.checkIssuesReadyForDeploy(issues);

        //then
        assertThat(result.getResult()).isEqualTo(DeploymentCheckResult.NOK);
        assertThat(result.getMessage())
                .contains("Issues without label: [JEAP-1234].")
                .contains("Issues not found in jira: [JEAP-9999].")
                .contains("Issues in jira projects not visible to the deployment log service: [UTF-8]");
        assertThat(result.getIssuesWithoutLabel()).containsExactly("JEAP-1234");
        assertThat(result.getIssuesNotFound()).containsExactly("JEAP-9999", "UTF-8");
        assertThat(result.getProjectsNotVisible()).containsExactly("UTF");
    }

    @Test
    void checkIssuesReadyForDeploy_whenNoIssues_thenReturnsOk() {
        //given
        when(jiraWebClient.searchIssuesLabels(Set.of())).thenReturn(searchResult(Map.of(), Set.of()));

        //when
        final DeploymentCheckResultDto result = service.checkIssuesReadyForDeploy(Set.of());

        //then
        assertThat(result.getResult()).isEqualTo(DeploymentCheckResult.OK);
    }

    @Test
    void checkIssuesReadyForDeploy_searchFailed_thenThrowsJiraUnavailableException() {
        //given
        final Set<String> issues = Set.of("JEAP-1234");
        when(jiraWebClient.searchIssuesLabels(issues))
                .thenThrow(new RestClientResponseException("my error message", 500, "status", null, null, null));

        //when / then
        assertThatThrownBy(() -> service.checkIssuesReadyForDeploy(issues))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("Jira is not available");
    }

    @Test
    void checkIssuesReadyForDeploy_visibleProjectsLookupFailed_thenThrowsJiraUnavailableException() {
        //given
        final Set<String> issues = Set.of("JEAP-1234");
        when(jiraWebClient.searchIssuesLabels(issues)).thenReturn(searchResult(Map.of(), Set.of("JEAP-1234")));
        when(jiraWebClient.getVisibleProjectKeys()).thenThrow(new ResourceAccessException("connection refused"));

        //when / then
        assertThatThrownBy(() -> service.checkIssuesReadyForDeploy(issues))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("Jira is not available");
    }

    private static JiraIssuesSearchResult searchResult(Map<String, List<String>> labelsByIssueKey, Set<String> notFoundIssueKeys) {
        return JiraIssuesSearchResult.builder()
                .labelsByIssueKey(labelsByIssueKey)
                .notFoundIssueKeys(notFoundIssueKeys)
                .build();
    }
}
