package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectIssueDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePageRepository;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectPageDtoFactoryTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-09-02T10:00:00+02:00");

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private JiraIssuePageRepository jiraIssuePageRepository;

    private JiraProjectPageDtoFactory factory;

    @BeforeEach
    void setUp() {
        DocumentationGeneratorConfluenceProperties confluenceProperties =
                new DocumentationGeneratorConfluenceProperties();
        confluenceProperties.setChangeViewActivityPeriod(Duration.ofDays(30));
        confluenceProperties.setSpaceKey("JMEAWS");
        confluenceProperties.setUrl("https://confluence.example");
        JiraWebClientProperties jiraProperties = new JiraWebClientProperties();
        jiraProperties.setUrl("https://jira.example");
        factory = new JiraProjectPageDtoFactory(deploymentRepository, confluenceProperties, jiraProperties,
                jiraIssuePageRepository);
    }

    @Test
    void normalizesAndDeduplicatesKeysAndCalculatesCodeDeploymentStatus() {
        Deployment recentJeap1 = deployment(NOW.minusDays(1), DeploymentState.STARTED,
                environment("DEV", 1, false), "jeap-1", "invalid key");
        Deployment duplicateJeap1 = deployment(NOW.minusDays(2), DeploymentState.STARTED,
                environment("DEV", 1, false), " JEAP-1 ");
        Deployment recentJeap2 = deployment(NOW.minusHours(1), DeploymentState.STARTED,
                environment("DEV", 1, false), "JEAP-2");
        Deployment recentOther = deployment(NOW.minusHours(2), DeploymentState.STARTED,
                environment("DEV", 1, false), "OPS_2-9");
        when(deploymentRepository.findDeploymentsWithJiraIssuesStartedAtOrAfter(NOW.minusDays(30)))
                .thenReturn(List.of(recentJeap2, recentJeap1, duplicateJeap1, recentOther));

        Deployment jeap1DevSuccess = deployment(NOW.minusDays(20), DeploymentState.SUCCESS,
                environment("DEV", 1, false), "JEAP-1");
        Deployment jeap1ProdFailure = deployment(NOW.minusDays(1), DeploymentState.FAILURE,
                environment("PROD", 3, true), "JEAP-1");
        Deployment jeap2ProdSuccess = deployment(NOW.minusHours(1), DeploymentState.SUCCESS,
                environment("PROD", 3, true), "JEAP-2");
        when(deploymentRepository.findCodeDeploymentsForJiraIssues(Set.of("JEAP-1", "JEAP-2", "OPS_2-9")))
                .thenReturn(List.of(jeap2ProdSuccess, jeap1ProdFailure, jeap1DevSuccess));
        when(jiraIssuePageRepository.findByIssueKey("JEAP-1"))
                .thenReturn(java.util.Optional.of(JiraIssuePage.create("JEAP-1", "issue-page", "project-page")));

        List<JiraProjectPageDto> pages = factory.createActiveProjects(NOW);

        assertThat(pages).extracting(JiraProjectPageDto::getProjectKey)
                .containsExactly("JEAP", "OPS_2");
        assertThat(pages).extracting(JiraProjectPageDto::getConfluenceSpaceKey)
                .containsOnly("JMEAWS");
        List<JiraProjectIssueDto> jeapIssues = pages.getFirst().getIssues();
        assertThat(jeapIssues).extracting(JiraProjectIssueDto::getIssueKey)
                .containsExactly("JEAP-1", "JEAP-2");
        assertThat(jeapIssues.getFirst().getHighestSuccessfulStage()).isEqualTo("DEV");
        assertThat(jeapIssues.getFirst().getFailedHigherStage()).isEqualTo("PROD");
        assertThat(jeapIssues.getFirst().isSuccessfullyDeployedToProduction()).isFalse();
        assertThat(jeapIssues.get(1).getHighestSuccessfulStage()).isEqualTo("PROD");
        assertThat(jeapIssues.get(1).isSuccessfullyDeployedToProduction()).isTrue();
        assertThat(pages.get(1).getIssues().getFirst().getHighestSuccessfulStage()).isEqualTo("N/A");
        assertThat(jeapIssues.getFirst().getJiraIssueUrl()).isEqualTo("https://jira.example/browse/JEAP-1");
        assertThat(jeapIssues.getFirst().getDeploymentLogIssuePageUrl())
                .isEqualTo("https://confluence.example/pages/viewpage.action?pageId=issue-page");
    }

    @Test
    void doesNotQueryHistoricalDeploymentsWhenThereAreNoValidActiveIssues() {
        Deployment invalid = deployment(NOW.minusDays(1), DeploymentState.STARTED,
                environment("DEV", 1, false), "not-an-issue");
        when(deploymentRepository.findDeploymentsWithJiraIssuesStartedAtOrAfter(NOW.minusDays(30)))
                .thenReturn(List.of(invalid));

        assertThat(factory.createActiveProjects(NOW)).isEmpty();

        verify(deploymentRepository, never()).findCodeDeploymentsForJiraIssues(anySet());
    }

    private Deployment deployment(ZonedDateTime startedAt, DeploymentState state, Environment environment,
                                  String... issueKeys) {
        Deployment deployment = mock(Deployment.class);
        Changelog changelog = mock(Changelog.class);
        lenient().when(deployment.getStartedAt()).thenReturn(startedAt);
        lenient().when(deployment.getState()).thenReturn(state);
        lenient().when(deployment.getEnvironment()).thenReturn(environment);
        lenient().when(deployment.getChangelog()).thenReturn(changelog);
        lenient().when(changelog.getJiraIssueKeys()).thenReturn(Set.of(issueKeys));
        return deployment;
    }

    private Environment environment(String name, int stagingOrder, boolean productive) {
        Environment environment = new Environment(name);
        environment.setStagingOrder(stagingOrder);
        environment.setProductive(productive);
        return environment;
    }
}
