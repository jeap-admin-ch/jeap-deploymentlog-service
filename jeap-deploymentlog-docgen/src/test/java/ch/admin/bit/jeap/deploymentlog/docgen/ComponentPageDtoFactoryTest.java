package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDto;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ComponentPageDtoFactoryTest {
    VersionDeploymentRepository versions = mock(VersionDeploymentRepository.class);
    DeploymentPageRepository pages = mock(DeploymentPageRepository.class);
    StagingEnvironmentResolver stages = mock(StagingEnvironmentResolver.class);
    Component component = new Component("component", new System("system"));
    Environment ref = new Environment("REF");
    Environment prod = new Environment("PROD");
    ZonedDateTime start = ZonedDateTime.parse("2026-09-25T10:00:00Z");
    ComponentPageDtoFactory factory;
    List<Deployment> recorded = new java.util.ArrayList<>();

    @BeforeEach void setup() {
        var confluence = new DocumentationGeneratorConfluenceProperties();
        confluence.setComponentFlowMaxShow(2);
        confluence.setUrl("https://confluence.example");
        var jira = new JiraWebClientProperties();
        jira.setUrl("https://jira.example");
        when(stages.resolveStartEnvironment()).thenReturn(ref);
        when(stages.resolveDefaultFinalDeploymentEnvironment()).thenReturn(prod);
        when(versions.history(component.getId())).thenAnswer(invocation -> recorded.stream()
                .map(d -> new StagingHistoryEntry(d.getId(), d.getComponentVersion().getVersionName(),
                        d.getEnvironment().getName(), d.getStartedAt(), d.getEndedAt(), d.getState(), false))
                .toList());
        factory = new ComponentPageDtoFactory(versions, pages, confluence, jira, stages);
    }

    @Test void rendersAllDeploymentsAndFirstSuccessfulLatencyWithJiraAndDetailLinks() {
        Deployment failed = deployment(ref, 0, DeploymentState.FAILURE);
        Deployment first = deployment(ref, 1, DeploymentState.SUCCESS);
        Deployment end = deployment(prod, 3, DeploymentState.SUCCESS);
        Deployment retry = deployment(ref, 4, DeploymentState.SUCCESS);
        when(versions.findLatestVersions(component.getId(), 2)).thenReturn(List.of(List.of(failed, first, end, retry)));
        DeploymentPage page = DeploymentPage.builder().id(UUID.randomUUID()).deploymentId(end.getId())
                .pageId("detail").lastUpdatedAt(start).deploymentStateTimestamp(start).build();
        when(pages.findDeploymentPageByDeploymentId(end.getId())).thenReturn(Optional.of(page));
        ComponentFlowDto result = factory.create(component).getFlows().getFirst();
        assertThat(result.getDuration()).isEqualTo("02:00:00");
        assertThat(result.getDeployments()).extracting("stage").containsExactly("REF", "PROD", "REF", "REF");
        assertThat(result.getDeployments().get(1).getPageUrl()).isEqualTo("https://confluence.example/pages/viewpage.action?pageId=detail");
        assertThat(result.getJiraIssues()).extracting("key").containsExactly("ABC-1", "ABC-2");
        assertThat(result.getJiraIssues()).extracting("url").containsExactly("https://jira.example/browse/ABC-1", "https://jira.example/browse/ABC-2");
    }

    @Test void missingSuccessfulStartHasNoLatencyAndHistoricalTypeRemainsAbsent() {
        Deployment historic = deployment(prod, 1, DeploymentState.SUCCESS);
        historic.classify(null);
        when(versions.findLatestVersions(component.getId(), 2)).thenReturn(List.of(List.of(historic)));
        var result = factory.create(component).getFlows().getFirst();
        assertThat(result.getDuration()).isNull();
        assertThat(result.getDeployments().getFirst().getType()).isNull();
    }

    @Test void endBeforeStartDoesNotProduceNegativeLatency() {
        when(versions.findLatestVersions(component.getId(), 2)).thenReturn(List.of(List.of(
                deployment(prod, 0, DeploymentState.SUCCESS), deployment(ref, 1, DeploymentState.SUCCESS))));
        assertThat(factory.create(component).getFlows().getFirst().getDuration()).isNull();
    }

    @Test void latencySurvivesRetentionOfTheFirstSuccessfulDeployment() {
        deployment(ref, 0, DeploymentState.SUCCESS);
        Deployment end = deployment(prod, 3, DeploymentState.SUCCESS);
        when(versions.findLatestVersions(component.getId(), 2)).thenReturn(List.of(List.of(end)));
        assertThat(factory.create(component).getFlows().getFirst().getDuration()).isEqualTo("03:00:00");
    }

    Deployment deployment(Environment environment, int hours, DeploymentState state) {
        var d = Deployment.builder().externalId(UUID.randomUUID().toString()).startedBy("test")
                .startedAt(start.plusHours(hours)).environment(environment).sequence(DeploymentSequence.NEW)
                .componentVersion(ComponentVersion.builder().component(component).versionName("1.0")
                        .versionControlUrl("https://git").commitRef("abc").committedAt(start)
                        .deploymentUnit(DeploymentUnit.builder().type(DeploymentUnitType.DOCKER_IMAGE)
                                .coordinates("image:1.0").artifactRepositoryUrl("https://registry").build()).build())
                .changelog(Changelog.builder().jiraIssueKeys(Set.of("abc-2", "ABC-1")).build()).build();
        d.classify(DeploymentStagingType.NEW);
        if (state == DeploymentState.SUCCESS) d.success(d.getStartedAt().plusMinutes(5), "ok");
        else d.failed(d.getStartedAt().plusMinutes(5), "failed");
        recorded.add(d);
        return d;
    }
}
