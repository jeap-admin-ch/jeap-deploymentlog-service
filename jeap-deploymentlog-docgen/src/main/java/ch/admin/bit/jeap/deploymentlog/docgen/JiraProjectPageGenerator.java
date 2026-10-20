package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectIssueDto;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLockTimeoutException;
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
import java.util.TreeSet;
import java.util.function.Consumer;
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
    private final DocumentationStructureLock documentationStructureLock;

    /**
     * Regenerates the change view of every active project and clears the page of every tracked project that became
     * inactive. Each project is read and written under its own lock, so a concurrent refresh of the same project
     * cannot be overwritten with older data. A project whose lock is busy does not stop the others, see
     * {@link #forEachProjectUnderLock}.
     */
    void generateAll(String changesPageId) {
        Set<String> projectKeys = new TreeSet<>(dtoFactory.activeProjectKeys(ZonedDateTime.now()));
        pageRepository.findAll().forEach(page -> projectKeys.add(page.getProjectKey()));
        forEachProjectUnderLock(projectKeys, projectKey -> generateAllOfProject(changesPageId, projectKey));
    }

    private void generateAllOfProject(String changesPageId, String projectKey) {
        Optional<JiraProjectPageDto> activeProject = dtoFactory.createActiveProject(projectKey, ZonedDateTime.now());
        if (activeProject.isPresent()) {
            generatePage(changesPageId, activeProject.get(), null);
        } else if (pageRepository.findByProjectKey(projectKey).isPresent()) {
            JiraProjectPageDto inactiveProject = JiraProjectPageDto.builder()
                    .projectKey(projectKey)
                    .issues(List.of())
                    .build();
            generatePage(changesPageId, inactiveProject, Set.of());
        }
    }

    /**
     * Callers hold the project's lock.
     */
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
        forEachProjectUnderLock(issueKeysByProject.keySet(), projectKey -> pageRepository.findByProjectKey(projectKey)
                .ifPresent(projectPage -> jiraIssuePageGenerator.generatePages(
                        projectPage.getPageId(), projectKey, issueKeysByProject.get(projectKey))));
    }

    /**
     * Runs the task for each project under that project's lock. A lock held elsewhere, typically by a refresh on
     * another instance, only skips its own project: the remaining projects are still processed, and the timeout is
     * rethrown at the end so that the caller defers and retries the run as before.
     */
    private void forEachProjectUnderLock(Set<String> projectKeys, Consumer<String> task) {
        DocgenLockTimeoutException lockTimeout = null;
        for (String projectKey : projectKeys) {
            try {
                documentationStructureLock.runWithJiraProjectLock(projectKey, () -> {
                    task.accept(projectKey);
                    return null;
                });
            } catch (DocgenLockTimeoutException ex) {
                log.info("Skipping the Jira change view of project '{}', its lock is held elsewhere", projectKey);
                if (lockTimeout == null) {
                    lockTimeout = ex;
                } else {
                    lockTimeout.addSuppressed(ex);
                }
            }
        }
        if (lockTimeout != null) {
            throw lockTimeout;
        }
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
