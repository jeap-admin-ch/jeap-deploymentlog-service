package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraIssueDeploymentDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraIssuePageDto;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class JiraIssuePageDtoFactory {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Comparator<Deployment> DEPLOYMENT_COMPARATOR =
            Comparator.comparing(Deployment::getStartedAt).reversed()
                    .thenComparing(Deployment::getExternalId)
                    .thenComparing(deployment -> deployment.getId().toString());

    private final DeploymentRepository deploymentRepository;
    private final DeploymentPageRepository deploymentPageRepository;
    private final DocumentationGeneratorConfluenceProperties confluenceProperties;
    private final JiraWebClientProperties jiraProperties;

    List<JiraIssuePageDto> create(Collection<String> requestedIssueKeys) {
        SortedSet<String> issueKeys = requestedIssueKeys.stream()
                .map(JiraIssueKey::parse)
                .flatMap(Optional::stream)
                .map(JiraIssueKey.Parsed::issueKey)
                .collect(Collectors.toCollection(TreeSet::new));
        if (issueKeys.isEmpty()) {
            return List.of();
        }
        List<Deployment> deployments = deploymentRepository.findDeploymentsForJiraIssues(issueKeys);
        Map<UUID, String> pageIds = deploymentPageRepository.findDeploymentPagesByDeploymentIds(
                        deployments.stream().map(Deployment::getId).collect(Collectors.toSet())).stream()
                .filter(page -> page.getPageId() != null && !page.getPageId().isBlank())
                .collect(Collectors.toMap(DeploymentPage::getDeploymentId, DeploymentPage::getPageId,
                        (left, right) -> left));

        Map<String, List<Deployment>> deploymentsByIssue = new HashMap<>();
        deployments.forEach(deployment -> JiraProjectPageDtoFactory.normalizedIssueKeys(deployment).stream()
                .filter(issueKeys::contains)
                .forEach(issueKey -> deploymentsByIssue
                        .computeIfAbsent(issueKey, ignored -> new ArrayList<>())
                        .add(deployment)));

        return issueKeys.stream()
                .map(issueKey -> JiraIssuePageDto.builder()
                        .issueKey(issueKey)
                        .jiraIssueUrl(jiraIssueUrl(issueKey))
                        .deployments(deploymentsByIssue.getOrDefault(issueKey, List.of()).stream()
                                .distinct()
                                .sorted(DEPLOYMENT_COMPARATOR)
                                .map(deployment -> toDto(deployment, pageIds.get(deployment.getId())))
                                .toList())
                        .build())
                .toList();
    }

    String confluencePageUrl(String pageId) {
        String confluenceUrl = confluenceProperties.getUrl();
        if (pageId == null || pageId.isBlank() || confluenceUrl == null || confluenceUrl.isBlank()) {
            return null;
        }
        return (confluenceUrl.endsWith("/") ? confluenceUrl : confluenceUrl + "/")
                + "pages/viewpage.action?pageId=" + pageId;
    }

    private JiraIssueDeploymentDto toDto(Deployment deployment, String pageId) {
        var componentVersion = deployment.getComponentVersion();
        var component = componentVersion.getComponent();
        String types = deployment.getDeploymentTypes().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(", "));
        return JiraIssueDeploymentDto.builder()
                .startedAt(deployment.getStartedAt().withZoneSameInstant(ZoneId.systemDefault())
                        .format(DATE_TIME_FORMATTER))
                .stage(deployment.getEnvironment().getName())
                .system(component.getSystem().getName())
                .component(component.getName())
                .version(componentVersion.getVersionName())
                .deploymentTypes(types.isEmpty() ? "-" : types)
                .state(deployment.getState().name())
                .deploymentPageUrl(confluencePageUrl(pageId))
                .startedBy(deployment.getStartedBy())
                .build();
    }

    private String jiraIssueUrl(String issueKey) {
        String jiraUrl = jiraProperties.getUrl();
        if (jiraUrl == null || jiraUrl.isBlank()) {
            return null;
        }
        return (jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/") + "browse/" + issueKey;
    }
}
