package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDeploymentDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentFlowDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraIssueDto;
import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Slf4j
class ComponentPageDtoFactory {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FlowRepository flowRepository;
    private final DeploymentPageRepository deploymentPageRepository;
    private final DocumentationGeneratorConfluenceProperties confluenceProperties;
    private final JiraWebClientProperties jiraProperties;

    ComponentPageDto create(Component component) {
        List<ComponentFlowDto> flows = flowRepository
                .findLatestForComponent(component.getId(), confluenceProperties.getComponentFlowMaxShow()).stream()
                .map(this::toDto)
                .toList();
        return ComponentPageDto.builder()
                .componentName(component.getName())
                .flowMaxShow(confluenceProperties.getComponentFlowMaxShow())
                .flows(flows)
                .build();
    }

    private ComponentFlowDto toDto(Flow flow) {
        List<Deployment> deployments = flow.getDeployments();
        return ComponentFlowDto.builder()
                .flowId(flow.getId().toString())
                .version(flow.getComponentVersion().getVersionName())
                .versionControlUrl(flow.getComponentVersion().getVersionControlUrl())
                .bornAt(format(flow.getBornAt()))
                .duration(successfulDuration(flow, deployments))
                .type(flow.getType().name())
                .state(flow.getState().name())
                .targetStage(flow.getFinalDeploymentEnvironment().getName())
                .deployments(deployments.stream()
                        .sorted(Comparator.comparing(Deployment::getStartedAt, Comparator.reverseOrder())
                                .thenComparing(Deployment::getId))
                        .map(this::toDeploymentDto)
                        .toList())
                .jiraIssues(jiraIssues(deployments))
                .build();
    }

    private ComponentFlowDeploymentDto toDeploymentDto(Deployment deployment) {
        String pageUrl = deploymentPageRepository.findDeploymentPageByDeploymentId(deployment.getId())
                .map(page -> deploymentPageUrl(page.getPageId()))
                .orElse(null);
        return ComponentFlowDeploymentDto.builder()
                .startedAt(format(deployment.getStartedAt()))
                .stage(deployment.getEnvironment().getName())
                .state(deployment.getState().name())
                .pageUrl(pageUrl)
                .build();
    }

    private String deploymentPageUrl(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return null;
        }
        String confluenceUrl = confluenceProperties.getUrl();
        if (confluenceUrl == null || confluenceUrl.isBlank()) {
            return null;
        }
        return (confluenceUrl.endsWith("/") ? confluenceUrl : confluenceUrl + "/")
                + "pages/viewpage.action?pageId=" + pageId;
    }

    private List<JiraIssueDto> jiraIssues(List<Deployment> deployments) {
        return deployments.stream()
                .map(Deployment::getChangelog)
                .filter(Objects::nonNull)
                .map(Changelog::getJiraIssueKeys)
                .filter(Objects::nonNull)
                .flatMap(java.util.Collection::stream)
                .filter(key -> key != null && !key.isBlank())
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .map(key -> JiraIssueDto.builder().key(key).url(jiraIssueUrl(key)).build())
                .toList();
    }

    private String jiraIssueUrl(String issueKey) {
        String jiraUrl = jiraProperties.getUrl();
        if (jiraUrl == null || jiraUrl.isBlank()) {
            return null;
        }
        return (jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/") + "browse/" + issueKey;
    }

    private String successfulDuration(Flow flow, List<Deployment> deployments) {
        if (flow.getState() != FlowState.CLOSED) {
            return null;
        }
        return deployments.stream()
                .filter(deployment -> deployment.getState() == DeploymentState.SUCCESS)
                .filter(deployment -> Objects.equals(deployment.getEnvironment().getId(),
                        flow.getFinalDeploymentEnvironment().getId()))
                .map(Deployment::getEndedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(endedAt -> formatDuration(flow, endedAt))
                .orElse(null);
    }

    private String formatDuration(Flow flow, ZonedDateTime endedAt) {
        Duration duration = Duration.between(flow.getBornAt(), endedAt);
        if (duration.isNegative()) {
            log.warn("Ignoring negative component flow duration for flow {}", flow.getId());
            return null;
        }
        return "%02d:%02d:%02d".formatted(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    private String format(ZonedDateTime timestamp) {
        return timestamp.withZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }
}
