package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraIssuePageDto;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraIssuePageDtoFactoryTest {

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private DeploymentPageRepository deploymentPageRepository;
    private JiraIssuePageDtoFactory factory;

    @BeforeEach
    void setUp() {
        DocumentationGeneratorConfluenceProperties confluence = new DocumentationGeneratorConfluenceProperties();
        confluence.setUrl("https://confluence.example");
        JiraWebClientProperties jira = new JiraWebClientProperties();
        jira.setUrl("https://jira.example");
        factory = new JiraIssuePageDtoFactory(deploymentRepository, deploymentPageRepository, confluence, jira);
    }

    @Test
    void includesAllTypesSortsNewestFirstAndOmitsMissingDeploymentLink() {
        Deployment older = deployment("older", ZonedDateTime.of(
                        2026, 7, 5, 10, 0, 0, 0, ZoneId.systemDefault()),
                DeploymentState.FAILURE, Set.of(DeploymentType.INFRASTRUCTURE), " JEAP-1 ");
        Deployment newer = deployment("newer", ZonedDateTime.of(
                        2026, 7, 6, 14, 45, 16, 0, ZoneId.systemDefault()),
                DeploymentState.SUCCESS, Set.of(DeploymentType.CONFIG, DeploymentType.CODE), "jeap-1", "JEAP-1");
        when(deploymentRepository.findDeploymentsForJiraIssues(Set.of("JEAP-1")))
                .thenReturn(List.of(older, newer));
        UUID olderId = older.getId();
        UUID newerId = newer.getId();
        ZonedDateTime newerStartedAt = newer.getStartedAt();
        when(deploymentPageRepository.findDeploymentPagesByDeploymentIds(Set.of(olderId, newerId)))
                .thenReturn(List.of(DeploymentPage.builder().id(UUID.randomUUID()).deploymentId(newerId)
                        .pageId("deployment-page").lastUpdatedAt(newerStartedAt)
                        .deploymentStateTimestamp(newerStartedAt).build()));

        JiraIssuePageDto page = factory.create(Set.of(" jeap-1 ")).getFirst();

        assertThat(page.getIssueKey()).isEqualTo("JEAP-1");
        assertThat(page.getJiraIssueUrl()).isEqualTo("https://jira.example/browse/JEAP-1");
        assertThat(page.getDeployments()).extracting(d -> d.getStartedAt())
                .containsExactly("2026-07-06 14:45:16", "2026-07-05 10:00:00");
        assertThat(page.getDeployments().getFirst().getDeploymentTypes()).isEqualTo("CODE, CONFIG");
        assertThat(page.getDeployments().getFirst().getDeploymentPageUrl())
                .isEqualTo("https://confluence.example/pages/viewpage.action?pageId=deployment-page");
        assertThat(page.getDeployments().get(1).getDeploymentPageUrl()).isNull();
    }

    @Test
    void producesEmptyPageWithoutQueriesForInvalidKeys() {
        assertThat(factory.create(Set.of("invalid"))).isEmpty();
        verifyNoInteractions(deploymentRepository, deploymentPageRepository);
    }

    private Deployment deployment(String externalId, ZonedDateTime startedAt, DeploymentState state,
                                  Set<DeploymentType> types, String... issueKeys) {
        Deployment deployment = mock(Deployment.class);
        ComponentVersion version = mock(ComponentVersion.class);
        Component component = mock(Component.class);
        System system = mock(System.class);
        Environment environment = new Environment("PROD");
        Changelog changelog = mock(Changelog.class);
        UUID id = UUID.randomUUID();
        when(deployment.getId()).thenReturn(id);
        lenient().when(deployment.getExternalId()).thenReturn(externalId);
        when(deployment.getStartedAt()).thenReturn(startedAt);
        when(deployment.getState()).thenReturn(state);
        when(deployment.getStartedBy()).thenReturn("John Doe");
        when(deployment.getEnvironment()).thenReturn(environment);
        when(deployment.getDeploymentTypes()).thenReturn(types);
        when(deployment.getComponentVersion()).thenReturn(version);
        when(version.getVersionName()).thenReturn("5.0.2");
        when(version.getComponent()).thenReturn(component);
        when(component.getName()).thenReturn("service");
        when(component.getSystem()).thenReturn(system);
        when(system.getName()).thenReturn("WVS");
        when(deployment.getChangelog()).thenReturn(changelog);
        when(changelog.getJiraIssueKeys()).thenReturn(Set.of(issueKeys));
        return deployment;
    }
}
