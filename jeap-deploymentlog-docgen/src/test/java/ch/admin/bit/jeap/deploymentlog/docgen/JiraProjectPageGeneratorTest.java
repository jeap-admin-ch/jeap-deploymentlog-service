package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectIssueDto;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLockTimeoutException;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectPageGeneratorTest {

    @Mock
    private ConfluenceAdapter confluenceAdapter;
    @Mock
    private TemplateRenderer templateRenderer;
    @Mock
    private JiraProjectPageDtoFactory dtoFactory;
    @Mock
    private JiraProjectPageRepository pageRepository;
    @Mock
    private JiraIssuePageGenerator jiraIssuePageGenerator;
    @Mock
    private DocumentationStructureLock documentationStructureLock;

    private JiraProjectPageGenerator generator;
    private JiraProjectPageDto dto;

    @BeforeEach
    void setUp() {
        generator = new JiraProjectPageGenerator(confluenceAdapter, templateRenderer, dtoFactory, pageRepository,
                jiraIssuePageGenerator, documentationStructureLock);
        dto = JiraProjectPageDto.builder().projectKey("JEAP").activityPeriod(Duration.ofDays(30))
                .issues(List.of()).build();
        lenient().when(dtoFactory.activeProjectKeys(any())).thenReturn(Set.of("JEAP"));
        lenient().when(dtoFactory.createActiveProject(eq("JEAP"), any())).thenReturn(Optional.of(dto));
        lenient().when(documentationStructureLock.runWithJiraProjectLock(anyString(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        lenient().when(pageRepository.findAll()).thenReturn(List.of());
        lenient().when(templateRenderer.renderJiraProjectPage(dto)).thenReturn("content");
        lenient().when(jiraIssuePageGenerator.generatePages(anyString(), anyString(), anyCollection()))
                .thenReturn(java.util.Map.of());
    }

    @Test
    void createsAndTracksPageOnlyAfterSuccessfulConfluenceOperation() {
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP (Jira)")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("changes-page"), eq("JEAP (Jira)"), any()))
                .thenReturn("project-page");

        generator.generateAll("changes-page");

        ArgumentCaptor<JiraProjectPage> captor = ArgumentCaptor.forClass(JiraProjectPage.class);
        verify(pageRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectKey()).isEqualTo("JEAP");
        assertThat(captor.getValue().getPageId()).isEqualTo("project-page");
    }

    @Test
    void reusesTrackedPageId() {
        JiraProjectPage tracked = JiraProjectPage.create("JEAP", "project-page", "changes-page");
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.of(tracked));
        when(confluenceAdapter.updatePageById(eq("project-page"), eq("changes-page"), eq("JEAP (Jira)"),
                any(), eq(false))).thenReturn(true);

        generator.generateAll("changes-page");

        verify(confluenceAdapter, never()).addOrUpdatePageUnderAncestor(any(), any(), any());
        verify(pageRepository).save(tracked);
    }

    @Test
    void adoptsLegacyProjectPageAndAddsJiraSuffix() {
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP (Jira)")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP")).thenReturn(Optional.of("legacy-page"));
        when(confluenceAdapter.updatePageById(eq("legacy-page"), eq("changes-page"), eq("JEAP (Jira)"),
                any(), eq(false))).thenReturn(true);

        generator.generateAll("changes-page");

        verify(confluenceAdapter, never()).addOrUpdatePageUnderAncestor(any(), any(), any());
        verify(pageRepository).save(argThat(page -> page.getPageId().equals("legacy-page")));
    }

    @Test
    void preservesTrackingWhenConfluenceFails() {
        JiraProjectPage tracked = JiraProjectPage.create("JEAP", "project-page", "changes-page");
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.of(tracked));
        when(confluenceAdapter.updatePageById(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("Confluence unavailable"));

        assertThatThrownBy(() -> generator.generateAll("changes-page"))
                .isInstanceOf(IllegalStateException.class);

        verify(pageRepository, never()).save(any());
        assertThat(tracked.getPageId()).isEqualTo("project-page");
    }

    @Test
    void clearsTrackedProjectPageAfterItsLastIssueBecameInactive() {
        JiraProjectPage tracked = JiraProjectPage.create("OLD", "old-page", "changes-page");
        when(dtoFactory.activeProjectKeys(any())).thenReturn(Set.of());
        when(pageRepository.findAll()).thenReturn(List.of(tracked));
        when(pageRepository.findByProjectKey("OLD")).thenReturn(Optional.of(tracked));
        when(confluenceAdapter.updatePageById(eq("old-page"), eq("changes-page"), eq("OLD (Jira)"),
                any(), eq(false))).thenAnswer(invocation -> {
                    invocation.getArgument(3, Supplier.class).get();
                    return true;
                });

        generator.generateAll("changes-page");

        verify(templateRenderer).renderJiraProjectPage(argThat(page -> page.getProjectKey().equals("OLD")
                && page.getIssues().isEmpty()));
        verify(pageRepository).save(tracked);
    }

    @Test
    void addsGeneratedIssuePageLinkToProjectPageInSameRun() {
        JiraProjectPageDto project = JiraProjectPageDto.builder().projectKey("JEAP")
                .activityPeriod(Duration.ofDays(30))
                .issues(List.of(JiraProjectIssueDto.builder().issueKey("JEAP-1").build()))
                .build();
        when(dtoFactory.createActiveProject(eq("JEAP"), any())).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP (Jira)")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("changes-page"), eq("JEAP (Jira)"), any()))
                .thenReturn("project-page");
        when(jiraIssuePageGenerator.generatePages("project-page", "JEAP", java.util.Set.of("JEAP-1")))
                .thenReturn(java.util.Map.of("JEAP-1", "https://confluence/issue-page"));
        when(confluenceAdapter.updatePageById(eq("project-page"), eq("changes-page"), eq("JEAP (Jira)"),
                any(), eq(false))).thenAnswer(invocation -> {
                    invocation.getArgument(3, Supplier.class).get();
                    return true;
                });

        generator.generateAll("changes-page");

        verify(templateRenderer).renderJiraProjectPage(argThat(rendered ->
                "https://confluence/issue-page".equals(
                        rendered.getIssues().getFirst().getDeploymentLogIssuePageUrl())));
    }

    @Test
    void writesTrackedProjectPageOnceWhenItsIssuePageLinksAreUnchanged() {
        JiraProjectPageDto project = JiraProjectPageDto.builder().projectKey("JEAP")
                .activityPeriod(Duration.ofDays(30))
                .issues(List.of(JiraProjectIssueDto.builder().issueKey("JEAP-1")
                        .deploymentLogIssuePageUrl("https://confluence/issue-page").build()))
                .build();
        when(dtoFactory.createActiveProject(eq("JEAP"), any())).thenReturn(Optional.of(project));
        JiraProjectPage tracked = JiraProjectPage.create("JEAP", "project-page", "changes-page");
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.of(tracked));
        when(confluenceAdapter.updatePageById(eq("project-page"), eq("changes-page"), eq("JEAP (Jira)"),
                any(), eq(false))).thenReturn(true);
        when(jiraIssuePageGenerator.generatePages("project-page", "JEAP", Set.of("JEAP-1")))
                .thenReturn(java.util.Map.of("JEAP-1", "https://confluence/issue-page"));

        generator.generateForProject("changes-page", "JEAP", Set.of("JEAP-1"));

        verify(confluenceAdapter, times(1)).updatePageById(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void generatesOnlyTheRequestedIssuesOfTheProject() {
        JiraProjectPageDto jeap = JiraProjectPageDto.builder().projectKey("JEAP")
                .activityPeriod(Duration.ofDays(30))
                .issues(List.of(JiraProjectIssueDto.builder().issueKey("JEAP-1").build(),
                        JiraProjectIssueDto.builder().issueKey("JEAP-2").build()))
                .build();
        when(dtoFactory.createActiveProject(eq("JEAP"), any())).thenReturn(Optional.of(jeap));
        when(pageRepository.findByProjectKey("JEAP"))
                .thenReturn(Optional.of(JiraProjectPage.create("JEAP", "project-page", "changes-page")));
        when(confluenceAdapter.updatePageById(eq("project-page"), eq("changes-page"), eq("JEAP (Jira)"),
                any(), eq(false))).thenReturn(true);

        generator.generateForProject("changes-page", "JEAP", Set.of("JEAP-2"));

        verify(jiraIssuePageGenerator).generatePages("project-page", "JEAP", Set.of("JEAP-2"));
        verify(dtoFactory, never()).activeProjectKeys(any());
    }

    @Test
    void generatesEveryProjectUnderItsOwnLock() {
        JiraProjectPageDto other = JiraProjectPageDto.builder().projectKey("ABC").activityPeriod(Duration.ofDays(30))
                .issues(List.of()).build();
        when(dtoFactory.activeProjectKeys(any())).thenReturn(Set.of("JEAP", "ABC"));
        when(dtoFactory.createActiveProject(eq("ABC"), any())).thenReturn(Optional.of(other));
        List<String> lockedProjects = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            lockedProjects.add(invocation.getArgument(0));
            return invocation.<Supplier<?>>getArgument(1).get();
        }).when(documentationStructureLock).runWithJiraProjectLock(anyString(), any());
        doAnswer(invocation -> {
            assertThat(lockedProjects).last().isEqualTo("JEAP");
            return Optional.of(dto);
        }).when(dtoFactory).createActiveProject(eq("JEAP"), any());
        when(pageRepository.findByProjectKey(anyString())).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(anyString(), anyString(), any())).thenReturn("page");

        generator.generateAll("changes-page");

        assertThat(lockedProjects).containsExactly("ABC", "JEAP");
    }

    @Test
    void generatesTheRemainingProjectsWhenOneProjectLockIsBusy() {
        when(dtoFactory.activeProjectKeys(any())).thenReturn(Set.of("JEAP", "ABC"));
        DocgenLockTimeoutException lockTimeout = new DocgenLockTimeoutException("docgen:jira-project:ABC");
        doThrow(lockTimeout).when(documentationStructureLock).runWithJiraProjectLock(eq("ABC"), any());
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("changes-page"), eq("JEAP (Jira)"), any()))
                .thenReturn("project-page");

        assertThatThrownBy(() -> generator.generateAll("changes-page")).isSameAs(lockTimeout);

        verify(pageRepository).save(argThat(page -> page.getProjectKey().equals("JEAP")));
    }

    @Test
    void regeneratesTheRemainingTrackedIssuePagesWhenOneProjectLockIsBusy() {
        DocgenLockTimeoutException lockTimeout = new DocgenLockTimeoutException("docgen:jira-project:ABC");
        doThrow(lockTimeout).when(documentationStructureLock).runWithJiraProjectLock(eq("ABC"), any());
        when(pageRepository.findByProjectKey("JEAP"))
                .thenReturn(Optional.of(JiraProjectPage.create("JEAP", "project-page", "changes-page")));

        assertThatThrownBy(() -> generator.regenerateTrackedIssuePages(Set.of("ABC-1", "JEAP-1")))
                .isSameAs(lockTimeout);

        verify(jiraIssuePageGenerator).generatePages("project-page", "JEAP", Set.of("JEAP-1"));
    }

    @Test
    void regeneratesTrackedIssuePagesForNormalizedValidKeys() {
        JiraProjectPage tracked = JiraProjectPage.create("JEAP", "project-page", "changes-page");
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.of(tracked));

        generator.regenerateTrackedIssuePages(Set.of("jeap-2", "JEAP-1", "invalid"));

        verify(documentationStructureLock).runWithJiraProjectLock(eq("JEAP"), any());
        verify(jiraIssuePageGenerator).generatePages(
                "project-page", "JEAP", Set.of("JEAP-1", "JEAP-2"));
    }
}
