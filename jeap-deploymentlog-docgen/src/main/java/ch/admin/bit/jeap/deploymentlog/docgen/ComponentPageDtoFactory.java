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
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.StagingEnvironmentResolver;
import ch.admin.bit.jeap.deploymentlog.domain.VersionDeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.StagingHistoryEntry;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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
// Read from the writer: page generation must see the deployment and page tracking just committed.
@Transactional
class ComponentPageDtoFactory {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VersionDeploymentRepository versionDeploymentRepository;
    private final DeploymentPageRepository deploymentPageRepository;
    private final DocumentationGeneratorConfluenceProperties confluenceProperties;
    private final JiraWebClientProperties jiraProperties;
    private final StagingEnvironmentResolver stageResolver;

    ComponentPageDto create(Component component) {
        List<StagingHistoryEntry> history = versionDeploymentRepository.history(component.getId());
        List<List<Deployment>> versions = versionDeploymentRepository
                .findLatestVersions(component.getId(), confluenceProperties.getComponentFlowMaxShow());
        List<Environment> relevantStages = versions.isEmpty() ? List.of() : stageResolver.relevantEnvironments();
        List<ComponentFlowDto> flows = versions.stream()
                .map(deployments -> toDto(deployments, history, relevantStages))
                .toList();
        return ComponentPageDto.builder()
                .componentName(component.getName())
                .flowMaxShow(confluenceProperties.getComponentFlowMaxShow())
                .flows(flows)
                .build();
    }

    private ComponentFlowDto toDto(List<Deployment> deployments, List<StagingHistoryEntry> history,
                                   List<Environment> relevantStages) {
        Deployment first = deployments.getFirst();
        return ComponentFlowDto.builder()
                .version(first.getComponentVersion().getVersionName())
                .versionControlUrl(first.getComponentVersion().getVersionControlUrl())
                .bornAt(format(first.getStartedAt()))
                .duration(successfulDuration(history, first.getComponentVersion().getVersionName()))
                .deployments(deployments.stream()
                        .sorted(Comparator.comparing(Deployment::getStartedAt, Comparator.reverseOrder())
                                .thenComparing(Deployment::getId))
                        .map(deployment -> toDeploymentDto(deployment, relevantStages)).toList())
                .jiraIssues(jiraIssues(deployments))
                .build();
    }

    private ComponentFlowDeploymentDto toDeploymentDto(Deployment deployment, List<Environment> relevantStages) {
        String pageUrl = deploymentPageRepository.findDeploymentPageByDeploymentId(deployment.getId())
                .map(page -> deploymentPageUrl(page.getPageId()))
                .orElse(null);
        return ComponentFlowDeploymentDto.builder()
                .startedAt(format(deployment.getStartedAt()))
                .startedAtInstant(deployment.getStartedAt().toInstant())
                .stage(deployment.getEnvironment().getName())
                .state(deployment.getState().name())
                .type(deployment.getStagingType() == null ? null : deployment.getStagingType().name())
                .stagingOrder(deployment.getEnvironment().getStagingOrder())
                .stagingTarget(stagingTarget(deployment, relevantStages))
                .pageUrl(pageUrl)
                .build();
    }

    private String stagingTarget(Deployment deployment, List<Environment> relevantStages) {
        return relevantStages.stream()
                .filter(stage -> deployment.getFinalDeploymentEnvironments().stream()
                        .anyMatch(name -> stage.getName().equalsIgnoreCase(name.trim())))
                .max(Comparator.comparingInt(Environment::getStagingOrder))
                .map(Environment::getName)
                .orElseGet(() -> null);
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

    private String successfulDuration(List<StagingHistoryEntry> history, String version) {
        ZonedDateTime start = firstSuccess(history, version, stageResolver.resolveStartEnvironment().getName());
        ZonedDateTime end = firstSuccess(history, version, stageResolver.resolveDefaultFinalDeploymentEnvironment().getName());
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        Duration duration = Duration.between(start, end);
        return "%02d:%02d:%02d".formatted(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    private ZonedDateTime firstSuccess(List<StagingHistoryEntry> history, String version, String environment) {
        return history.stream().filter(d -> d.state() == DeploymentState.SUCCESS && !d.undeployment())
                .filter(d -> d.version().equals(version) && d.environment().equals(environment))
                .map(StagingHistoryEntry::startedAt).min(Comparator.naturalOrder()).orElse(null);
    }

    private String format(ZonedDateTime timestamp) {
        return timestamp.withZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }
}
