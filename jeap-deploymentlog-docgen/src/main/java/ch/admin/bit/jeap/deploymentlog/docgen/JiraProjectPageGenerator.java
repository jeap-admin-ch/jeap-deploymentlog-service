package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
class JiraProjectPageGenerator {

    private final ConfluenceAdapter confluenceAdapter;
    private final TemplateRenderer templateRenderer;
    private final JiraProjectPageDtoFactory dtoFactory;
    private final JiraProjectPageRepository pageRepository;

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
        generate(changesPageId, activeProjects);
        generate(changesPageId, inactiveTrackedProjects);
    }

    void generateForDeployment(String changesPageId, Deployment deployment) {
        Set<String> affectedProjectKeys = dtoFactory.projectKeys(deployment);
        if (affectedProjectKeys.isEmpty()) {
            return;
        }
        List<JiraProjectPageDto> affectedProjects = dtoFactory.createActiveProjects(ZonedDateTime.now()).stream()
                .filter(page -> affectedProjectKeys.contains(page.getProjectKey()))
                .toList();
        generate(changesPageId, affectedProjects);
    }

    private void generate(String changesPageId, List<JiraProjectPageDto> projectPages) {
        projectPages.forEach(project -> generatePage(changesPageId, project));
    }

    private String generatePage(String changesPageId, JiraProjectPageDto project) {
        String projectKey = project.getProjectKey();
        Optional<JiraProjectPage> trackedPage = pageRepository.findByProjectKey(projectKey);
        String pageId = trackedPage.map(JiraProjectPage::getPageId)
                .or(() -> confluenceAdapter.findPageByTitle(changesPageId, projectKey))
                .orElse(null);
        boolean moveRequired = trackedPage.map(JiraProjectPage::getParentPageId)
                .map(parent -> !Objects.equals(parent, changesPageId))
                .orElse(false);
        Supplier<String> content = () -> templateRenderer.renderJiraProjectPage(project);

        try {
            if (pageId == null || !confluenceAdapter.updatePageById(
                    pageId, changesPageId, projectKey, content, moveRequired)) {
                pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(changesPageId, projectKey, content);
            }

            JiraProjectPage page = trackedPage.orElse(null);
            if (page == null) {
                page = JiraProjectPage.create(projectKey, pageId, changesPageId);
            }
            page.updateLocation(pageId, changesPageId);
            pageRepository.save(page);
            return pageId;
        } catch (RuntimeException ex) {
            log.warn("Failed to generate Jira project page for project '{}'", projectKey, ex);
            throw ex;
        }
    }
}
