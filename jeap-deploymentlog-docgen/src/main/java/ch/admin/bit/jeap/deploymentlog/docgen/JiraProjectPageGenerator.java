package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectIssueDto;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
class JiraProjectPageGenerator {

    private final ConfluenceAdapter confluenceAdapter;
    private final TemplateRenderer templateRenderer;
    private final JiraProjectPageDtoFactory dtoFactory;
    private final JiraProjectPageRepository pageRepository;
    private final JiraIssuePageGenerator jiraIssuePageGenerator;

    void generateAll(String changesPageId) {
        List<JiraProjectPageDto> activeProjects = dtoFactory.createActiveProjects(ZonedDateTime.now());
        Set<String> activeProjectKeys = activeProjects.stream()
                .map(JiraProjectPageDto::getProjectKey)
                .collect(java.util.stream.Collectors.toSet());
        List<JiraProjectPageDto> inactiveTrackedProjects = pageRepository.findAll().stream()
                .map(JiraProjectPage::getProjectKey)
                .filter(projectKey -> !activeProjectKeys.contains(projectKey))
                .sorted()
                .map(projectKey -> JiraProjectPageDto.builder()
                        .projectKey(projectKey)
                        .issues(List.of())
                        .build())
                .toList();
        generate(changesPageId, activeProjects, null);
        generate(changesPageId, inactiveTrackedProjects, Set.of());
    }

    void generateForProject(String changesPageId, String projectKey, Set<String> affectedIssueKeys) {
        dtoFactory.createActiveProject(projectKey, ZonedDateTime.now())
                .ifPresent(project -> generatePage(changesPageId, project, affectedIssueKeys));
    }

    void regenerateTrackedIssuePages(Set<String> storedIssueKeys) {
        Map<String, Set<String>> issueKeysByProject = storedIssueKeys.stream()
                .map(JiraIssueKey::parse)
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.groupingBy(
                        JiraIssueKey.Parsed::projectKey,
                        TreeMap::new,
                        java.util.stream.Collectors.mapping(JiraIssueKey.Parsed::issueKey,
                                java.util.stream.Collectors.toCollection(java.util.TreeSet::new))));
        issueKeysByProject.forEach((projectKey, issueKeys) -> pageRepository.findByProjectKey(projectKey)
                .ifPresent(projectPage -> jiraIssuePageGenerator.generatePages(
                        projectPage.getPageId(), projectKey, issueKeys)));
    }

    private void generate(String changesPageId, List<JiraProjectPageDto> projectPages, Set<String> affectedIssueKeys) {
        projectPages.forEach(project -> generatePage(changesPageId, project, affectedIssueKeys));
    }

    private String generatePage(String changesPageId, JiraProjectPageDto project, Set<String> affectedIssueKeys) {
        String projectKey = project.getProjectKey();
        String pageTitle = projectKey + " (Jira)";
        Optional<JiraProjectPage> trackedPage = pageRepository.findByProjectKey(projectKey);
        String pageId = trackedPage.map(JiraProjectPage::getPageId)
                .or(() -> confluenceAdapter.findPageByTitle(changesPageId, pageTitle))
                .or(() -> confluenceAdapter.findPageByTitle(changesPageId, projectKey))
                .orElse(null);
        boolean moveRequired = trackedPage.map(JiraProjectPage::getParentPageId)
                .map(parent -> !Objects.equals(parent, changesPageId))
                .orElse(false);
        Set<String> issueKeysToGenerate = project.getIssues().stream()
                .map(JiraProjectIssueDto::getIssueKey)
                .filter(issueKey -> affectedIssueKeys == null || affectedIssueKeys.contains(issueKey))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        try {
            Supplier<String> content = () -> templateRenderer.renderJiraProjectPage(project);
            if (pageId == null || !confluenceAdapter.updatePageById(
                    pageId, changesPageId, pageTitle, content, moveRequired)) {
                pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(changesPageId, pageTitle, content);
            }
            JiraProjectPage page = trackedPage.orElse(null);
            if (page == null) {
                page = JiraProjectPage.create(projectKey, pageId, changesPageId);
            }
            page.updateLocation(pageId, changesPageId);
            pageRepository.save(page);

            // The issue pages need the project page as their parent, so their links reach the project page with a
            // second write. It is only needed for new or recreated issue pages: tracked ones keep their link.
            JiraProjectPageDto projectWithLinks = withIssuePageUrls(project,
                    jiraIssuePageGenerator.generatePages(pageId, projectKey, issueKeysToGenerate));
            if (!projectWithLinks.equals(project)) {
                confluenceAdapter.updatePageById(pageId, changesPageId, pageTitle,
                        () -> templateRenderer.renderJiraProjectPage(projectWithLinks), false);
            }
            return pageId;
        } catch (RuntimeException ex) {
            log.warn("Failed to generate Jira project page for project '{}'", projectKey, ex);
            throw ex;
        }
    }

    private static JiraProjectPageDto withIssuePageUrls(JiraProjectPageDto project, Map<String, String> issuePageUrls) {
        if (issuePageUrls.isEmpty()) {
            return project;
        }
        return project.toBuilder()
                .issues(project.getIssues().stream()
                        .map(issue -> issue.toBuilder()
                                .deploymentLogIssuePageUrl(issuePageUrls
                                        .getOrDefault(issue.getIssueKey(), issue.getDeploymentLogIssuePageUrl()))
                                .build())
                        .toList())
                .build();
    }
}
