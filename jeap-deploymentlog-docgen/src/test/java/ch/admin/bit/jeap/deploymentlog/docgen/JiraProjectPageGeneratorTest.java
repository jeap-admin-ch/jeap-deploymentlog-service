package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraProjectIssueDto;
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

    private JiraProjectPageGenerator generator;
    private JiraProjectPageDto dto;

    @BeforeEach
    void setUp() {
        generator = new JiraProjectPageGenerator(confluenceAdapter, templateRenderer, dtoFactory, pageRepository,
                jiraIssuePageGenerator);
        dto = JiraProjectPageDto.builder().projectKey("JEAP").activityPeriod(Duration.ofDays(30))
                .issues(List.of()).build();
        lenient().when(dtoFactory.createActiveProjects(any())).thenReturn(List.of(dto));
        lenient().when(pageRepository.findAll()).thenReturn(List.of());
        lenient().when(templateRenderer.renderJiraProjectPage(dto)).thenReturn("content");
        lenient().when(jiraIssuePageGenerator.generatePages(anyString(), anyString(), anyCollection()))
                .thenReturn(java.util.Map.of());
    }

    @Test
    void createsAndTracksPageOnlyAfterSuccessfulConfluenceOperation() {
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("changes-page"), eq("JEAP"), any()))
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
        when(confluenceAdapter.updatePageById(eq("project-page"), eq("changes-page"), eq("JEAP"),
                any(), eq(false))).thenReturn(true);

        generator.generateAll("changes-page");

        verify(confluenceAdapter, never()).addOrUpdatePageUnderAncestor(any(), any(), any());
        verify(pageRepository).save(tracked);
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
        when(dtoFactory.createActiveProjects(any())).thenReturn(List.of());
        when(pageRepository.findAll()).thenReturn(List.of(tracked));
        when(pageRepository.findByProjectKey("OLD")).thenReturn(Optional.of(tracked));
        when(confluenceAdapter.updatePageById(eq("old-page"), eq("changes-page"), eq("OLD"),
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
        when(dtoFactory.createActiveProjects(any())).thenReturn(List.of(project));
        when(pageRepository.findByProjectKey("JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("changes-page", "JEAP")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("changes-page"), eq("JEAP"), any()))
                .thenReturn("project-page");
        when(jiraIssuePageGenerator.generatePages("project-page", "JEAP", java.util.Set.of("JEAP-1")))
                .thenReturn(java.util.Map.of("JEAP-1", "https://confluence/issue-page"));
        when(confluenceAdapter.updatePageById(eq("project-page"), eq("changes-page"), eq("JEAP"),
                any(), eq(false))).thenAnswer(invocation -> {
                    invocation.getArgument(3, Supplier.class).get();
                    return true;
                });

        generator.generateAll("changes-page");

        verify(templateRenderer).renderJiraProjectPage(argThat(rendered ->
                "https://confluence/issue-page".equals(
                        rendered.getIssues().getFirst().getDeploymentLogIssuePageUrl())));
    }
}
