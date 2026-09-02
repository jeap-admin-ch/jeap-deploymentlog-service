package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentPageDto;
import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComponentPageDtoFactoryTest {

    @Mock
    private FlowRepository flowRepository;
    @Mock
    private DeploymentPageRepository deploymentPageRepository;

    private DocumentationGeneratorConfluenceProperties confluenceProperties;
    private ComponentPageDtoFactory factory;
    private Component component;

    @BeforeEach
    void setUp() {
        confluenceProperties = new DocumentationGeneratorConfluenceProperties();
        confluenceProperties.setComponentFlowMaxShow(2);
        confluenceProperties.setUrl("https://confluence.example");
        JiraWebClientProperties jiraProperties = new JiraWebClientProperties();
        jiraProperties.setUrl("https://jira.example");
        factory = new ComponentPageDtoFactory(flowRepository, deploymentPageRepository,
                confluenceProperties, jiraProperties);
        component = new Component("component", new System("system"));
    }

    @Test
    void mapsClosedFlowWithSuccessfulDurationNewestDeploymentsFirstAndDeterministicJiraIssues() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-08-01T10:00:00+02:00");
        Environment dev = new Environment("DEV");
        Environment prod = new Environment("PROD");
        Deployment first = deployment(dev, DeploymentState.FAILURE, bornAt.plusMinutes(5), null,
                Set.of("abc-2", "ABC-1"));
        Deployment second = deployment(prod, DeploymentState.SUCCESS, bornAt.plusMinutes(10),
                bornAt.plusHours(1).plusMinutes(2).plusSeconds(3), Set.of("ABC-1"));
        Flow flow = flow(FlowState.CLOSED, FlowType.NEW, bornAt, prod, List.of(first, second));
        when(flowRepository.findLatestForComponent(component.getId(), 2)).thenReturn(List.of(flow));
        UUID firstId = first.getId();
        UUID secondId = second.getId();
        DeploymentPage secondPage = DeploymentPage.builder().id(UUID.randomUUID()).deploymentId(secondId)
                .pageId("deployment-page").lastUpdatedAt(bornAt).deploymentStateTimestamp(bornAt).build();
        when(deploymentPageRepository.findDeploymentPageByDeploymentId(firstId)).thenReturn(Optional.empty());
        when(deploymentPageRepository.findDeploymentPageByDeploymentId(secondId)).thenReturn(Optional.of(secondPage));

        ComponentPageDto page = factory.create(component);

        verify(flowRepository).findLatestForComponent(component.getId(), 2);
        ComponentFlowDto result = page.getFlows().getFirst();
        assertThat(result.getType()).isEqualTo("NEW");
        assertThat(result.getDuration()).isEqualTo("01:02:03");
        assertThat(result.getDeployments()).extracting("stage").containsExactly("PROD", "DEV");
        assertThat(result.getDeployments()).extracting("pageUrl").containsExactly(
                "https://confluence.example/pages/viewpage.action?pageId=deployment-page", null);
        assertThat(result.getJiraIssues()).extracting("key").containsExactly("ABC-1", "ABC-2");
        assertThat(result.getJiraIssues()).extracting("url").containsExactly(
                "https://jira.example/browse/ABC-1", "https://jira.example/browse/ABC-2");
    }

    @Test
    void doesNotShowSuccessfulDurationForOpenOrAbortedFlows() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-08-01T10:00:00+02:00");
        Environment prod = new Environment("PROD");
        Flow open = flow(FlowState.OPEN, FlowType.RETRY, bornAt, prod, List.of());
        Flow aborted = flow(FlowState.ABORTED, FlowType.AD_HOC, bornAt.minusHours(1), prod, List.of());
        when(flowRepository.findLatestForComponent(component.getId(), 2)).thenReturn(List.of(open, aborted));

        ComponentPageDto page = factory.create(component);

        assertThat(page.getFlows()).extracting(ComponentFlowDto::getDuration).containsOnlyNulls();
        assertThat(page.getFlows()).extracting(ComponentFlowDto::getType).containsExactly("RETRY", "AD_HOC");
    }

    @Test
    void omitsNegativeClosedDurationAndHandlesMissingAbortReference() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-08-01T10:00:00+02:00");
        Environment prod = new Environment("PROD");
        Deployment inconsistentTargetDeployment = deployment(prod, DeploymentState.SUCCESS,
                bornAt.minusMinutes(2), bornAt.minusMinutes(1), Set.of());
        Flow closed = flow(FlowState.CLOSED, FlowType.ROLLBACK, bornAt, prod,
                List.of(inconsistentTargetDeployment));
        Flow abortedWithoutReference = flow(FlowState.ABORTED, FlowType.AD_HOC,
                bornAt.minusHours(1), prod, List.of());
        when(flowRepository.findLatestForComponent(component.getId(), 2))
                .thenReturn(List.of(closed, abortedWithoutReference));
        when(deploymentPageRepository.findDeploymentPageByDeploymentId(inconsistentTargetDeployment.getId()))
                .thenReturn(Optional.empty());

        ComponentPageDto page = factory.create(component);

        assertThat(page.getFlows().getFirst().getDuration()).isNull();
    }

    private Flow flow(FlowState state, FlowType type, ZonedDateTime bornAt, Environment target,
                      List<Deployment> deployments) {
        Flow flow = mock(Flow.class);
        ComponentVersion componentVersion = mock(ComponentVersion.class);
        when(flow.getId()).thenReturn(UUID.randomUUID());
        when(flow.getState()).thenReturn(state);
        when(flow.getType()).thenReturn(type);
        when(flow.getBornAt()).thenReturn(bornAt);
        when(flow.getFinalDeploymentEnvironment()).thenReturn(target);
        when(flow.getComponentVersion()).thenReturn(componentVersion);
        when(componentVersion.getVersionName()).thenReturn("1.0.0");
        when(componentVersion.getVersionControlUrl()).thenReturn("https://git.example/1.0.0");
        when(flow.getDeployments()).thenReturn(deployments);
        return flow;
    }

    private Deployment deployment(Environment environment, DeploymentState state, ZonedDateTime startedAt,
                                  ZonedDateTime endedAt, Set<String> issueKeys) {
        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn(UUID.randomUUID());
        when(deployment.getEnvironment()).thenReturn(environment);
        when(deployment.getState()).thenReturn(state);
        when(deployment.getStartedAt()).thenReturn(startedAt);
        lenient().when(deployment.getEndedAt()).thenReturn(endedAt);
        when(deployment.getChangelog()).thenReturn(Changelog.builder().jiraIssueKeys(issueKeys).build());
        return deployment;
    }
}
