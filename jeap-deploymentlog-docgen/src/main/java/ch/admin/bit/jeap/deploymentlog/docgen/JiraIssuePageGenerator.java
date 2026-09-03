package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraIssuePageDto;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
class JiraIssuePageGenerator {

    private final ConfluenceAdapter confluenceAdapter;
    private final TemplateRenderer templateRenderer;
    private final JiraIssuePageDtoFactory dtoFactory;
    private final JiraIssuePageRepository pageRepository;
    private final JiraAdapter jiraAdapter;

    Map<String, String> generatePages(String projectPageId, String projectKey, Collection<String> issueKeys) {
        Map<String, String> generatedPageUrls = new TreeMap<>();
        for (JiraIssuePageDto issue : dtoFactory.create(issueKeys)) {
            String pageId = generatePage(projectPageId, projectKey, issue);
            generatedPageUrls.put(issue.getIssueKey(), dtoFactory.confluencePageUrl(pageId));
            jiraAdapter.updateIssuePageRemoteLink(issue.getIssueKey(), pageId);
        }
        return generatedPageUrls;
    }

    private String generatePage(String projectPageId, String projectKey, JiraIssuePageDto issue) {
        String issueKey = issue.getIssueKey();
        Optional<JiraIssuePage> trackedPage = pageRepository.findByIssueKey(issueKey);
        String pageId = trackedPage.map(JiraIssuePage::getPageId)
                .or(() -> confluenceAdapter.findPageByTitle(projectPageId, issueKey))
                .orElse(null);
        boolean moveRequired = trackedPage.map(JiraIssuePage::getParentPageId)
                .map(parent -> !Objects.equals(parent, projectPageId))
                .orElse(false);
        Supplier<String> content = () -> templateRenderer.renderJiraIssuePage(issue);

        try {
            if (pageId == null || !confluenceAdapter.updatePageById(
                    pageId, projectPageId, issueKey, content, moveRequired)) {
                pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(projectPageId, issueKey, content);
            }

            JiraIssuePage page = trackedPage.orElse(null);
            if (page == null) {
                page = JiraIssuePage.create(issueKey, projectKey, pageId, projectPageId);
            }
            page.updateLocation(projectKey, pageId, projectPageId);
            pageRepository.save(page);
            return pageId;
        } catch (RuntimeException ex) {
            log.warn("Failed to generate Jira issue page for issue '{}' in project '{}'", issueKey, projectKey, ex);
            throw ex;
        }
    }
}
