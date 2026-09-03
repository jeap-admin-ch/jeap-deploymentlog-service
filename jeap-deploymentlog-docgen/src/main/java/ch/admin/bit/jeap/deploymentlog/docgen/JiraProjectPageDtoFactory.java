package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectIssueDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePageRepository;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class JiraProjectPageDtoFactory {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeploymentRepository deploymentRepository;
    private final DocumentationGeneratorConfluenceProperties confluenceProperties;
    private final JiraWebClientProperties jiraProperties;
    private final JiraIssuePageRepository jiraIssuePageRepository;

    List<JiraProjectPageDto> createActiveProjects(ZonedDateTime generatedAt) {
        ZonedDateTime activeSince = generatedAt.minus(confluenceProperties.getChangeViewActivityPeriod());
        List<Deployment> recentDeployments = deploymentRepository
                .findDeploymentsWithJiraIssuesStartedAtOrAfter(activeSince);

        Map<String, JiraIssueKey.Parsed> activeIssues = recentDeployments.stream()
                .flatMap(deployment -> issueKeys(deployment).stream())
                .map(JiraIssueKey::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(JiraIssueKey.Parsed::issueKey, Function.identity(), (left, right) -> left,
                        TreeMap::new));
        if (activeIssues.isEmpty()) {
            return List.of();
        }

        Map<String, ZonedDateTime> latestDeployments = latestDeploymentByIssue(recentDeployments);
        Map<String, List<Deployment>> codeDeployments = deploymentsByIssue(
                deploymentRepository.findCodeDeploymentsForJiraIssues(activeIssues.keySet()));

        return activeIssues.values().stream()
                .collect(Collectors.groupingBy(JiraIssueKey.Parsed::projectKey, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> JiraProjectPageDto.builder()
                        .projectKey(entry.getKey())
                        .confluenceSpaceKey(confluenceProperties.getSpaceKey())
                        .activityPeriod(confluenceProperties.getChangeViewActivityPeriod())
                        .issues(entry.getValue().stream()
                                .map(issue -> toIssueDto(issue.issueKey(), latestDeployments.get(issue.issueKey()),
                                        codeDeployments.getOrDefault(issue.issueKey(), List.of())))
                                .sorted(issueComparator())
                                .toList())
                        .build())
                .toList();
    }

    Set<String> projectKeys(Deployment deployment) {
        return issueKeys(deployment).stream()
                .map(JiraIssueKey::parse)
                .flatMap(Optional::stream)
                .map(JiraIssueKey.Parsed::projectKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private JiraProjectIssueDto toIssueDto(String issueKey, ZonedDateTime latestDeployment,
                                           List<Deployment> codeDeployments) {
        Optional<Deployment> highestSuccess = codeDeployments.stream()
                .filter(deployment -> deployment.getState() == DeploymentState.SUCCESS)
                .max(Comparator.comparingInt((Deployment deployment) -> deployment.getEnvironment().getStagingOrder())
                        .thenComparing(deployment -> deployment.getEnvironment().getName())
                        .thenComparing(Deployment::getStartedAt));
        int highestSuccessfulOrder = highestSuccess
                .map(deployment -> deployment.getEnvironment().getStagingOrder())
                .orElse(Integer.MIN_VALUE);
        Optional<Deployment> failedHigherDeployment = codeDeployments.stream()
                .filter(deployment -> deployment.getState() == DeploymentState.FAILURE)
                .filter(deployment -> deployment.getEnvironment().getStagingOrder() > highestSuccessfulOrder)
                .max(Comparator.comparingInt((Deployment deployment) -> deployment.getEnvironment().getStagingOrder())
                        .thenComparing(deployment -> deployment.getEnvironment().getName()));
        String failedHigherStage = failedHigherDeployment.isPresent()
                ? failedHigherDeployment.get().getEnvironment().getName()
                : null;
        boolean successfullyDeployedToProduction = codeDeployments.stream()
                .anyMatch(deployment -> deployment.getState() == DeploymentState.SUCCESS
                        && deployment.getEnvironment().isProductive());

        return JiraProjectIssueDto.builder()
                .issueKey(issueKey)
                .jiraIssueUrl(jiraIssueUrl(issueKey))
                .deploymentLogIssuePageUrl(jiraIssuePageRepository.findByIssueKey(issueKey)
                        .map(page -> confluencePageUrl(page.getPageId()))
                        .orElse(null))
                .latestDeploymentAt(format(latestDeployment))
                .highestSuccessfulStage(highestSuccess
                        .map(deployment -> deployment.getEnvironment().getName())
                        .orElse("N/A"))
                .failedHigherStage(failedHigherStage)
                .successfullyDeployedToProduction(successfullyDeployedToProduction)
                .build();
    }

    private Map<String, ZonedDateTime> latestDeploymentByIssue(List<Deployment> deployments) {
        Map<String, ZonedDateTime> result = new HashMap<>();
        deployments.forEach(deployment -> normalizedIssueKeys(deployment).forEach(issueKey ->
                result.merge(issueKey, deployment.getStartedAt(), (left, right) -> left.isAfter(right) ? left : right)));
        return result;
    }

    private Map<String, List<Deployment>> deploymentsByIssue(List<Deployment> deployments) {
        Map<String, List<Deployment>> result = new HashMap<>();
        deployments.forEach(deployment -> normalizedIssueKeys(deployment).forEach(issueKey ->
                result.computeIfAbsent(issueKey, ignored -> new ArrayList<>()).add(deployment)));
        return result;
    }

    static Set<String> normalizedIssueKeys(Deployment deployment) {
        return issueKeys(deployment).stream()
                .map(JiraIssueKey::parse)
                .flatMap(Optional::stream)
                .map(JiraIssueKey.Parsed::issueKey)
                .collect(Collectors.toSet());
    }

    private static Set<String> issueKeys(Deployment deployment) {
        Changelog changelog = deployment.getChangelog();
        return changelog == null || changelog.getJiraIssueKeys() == null
                ? Set.of()
                : changelog.getJiraIssueKeys();
    }

    private Comparator<JiraProjectIssueDto> issueComparator() {
        return Comparator.comparing(JiraProjectIssueDto::isSuccessfullyDeployedToProduction)
                .thenComparing(JiraProjectIssueDto::getLatestDeploymentAt, Comparator.reverseOrder())
                .thenComparing(JiraProjectIssueDto::getIssueKey);
    }

    private String jiraIssueUrl(String issueKey) {
        String jiraUrl = jiraProperties.getUrl();
        if (jiraUrl == null || jiraUrl.isBlank()) {
            return null;
        }
        return (jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/") + "browse/" + issueKey;
    }

    private String confluencePageUrl(String pageId) {
        String confluenceUrl = confluenceProperties.getUrl();
        if (pageId == null || pageId.isBlank() || confluenceUrl == null || confluenceUrl.isBlank()) {
            return null;
        }
        return (confluenceUrl.endsWith("/") ? confluenceUrl : confluenceUrl + "/")
                + "pages/viewpage.action?pageId=" + pageId;
    }

    private String format(ZonedDateTime timestamp) {
        return timestamp.withZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }
}
