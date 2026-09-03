package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.JiraIssuePageDto;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraIssuePageGeneratorTest {

    @Mock private ConfluenceAdapter confluenceAdapter;
    @Mock private TemplateRenderer templateRenderer;
    @Mock private JiraIssuePageDtoFactory dtoFactory;
    @Mock private JiraIssuePageRepository pageRepository;
    @Mock private JiraAdapter jiraAdapter;
    private JiraIssuePageGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new JiraIssuePageGenerator(confluenceAdapter, templateRenderer, dtoFactory, pageRepository,
                jiraAdapter);
        JiraIssuePageDto dto = JiraIssuePageDto.builder().issueKey("JEAP-1").deployments(List.of()).build();
        lenient().when(dtoFactory.create(anyCollection())).thenReturn(List.of(dto));
        lenient().when(dtoFactory.confluencePageUrl("issue-page")).thenReturn("https://confluence/issue-page");
    }

    @Test
    void createsTracksAndLinksIssueOnlyAfterConfluenceSucceeded() {
        when(pageRepository.findByIssueKey("JEAP-1")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("project-page", "JEAP-1")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("project-page"), eq("JEAP-1"), any()))
                .thenReturn("issue-page");

        Map<String, String> links = generator.generatePages("project-page", "JEAP", List.of("JEAP-1"));

        ArgumentCaptor<JiraIssuePage> captor = ArgumentCaptor.forClass(JiraIssuePage.class);
        verify(pageRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectKey()).isEqualTo("JEAP");
        assertThat(captor.getValue().getPageId()).isEqualTo("issue-page");
        assertThat(links).containsEntry("JEAP-1", "https://confluence/issue-page");
        verify(jiraAdapter).updateIssuePageRemoteLink("JEAP-1", "issue-page");
    }

    @Test
    void reusesTrackedPageAndMovesItWhenParentChanged() {
        JiraIssuePage tracked = JiraIssuePage.create("JEAP-1", "JEAP", "issue-page", "old-project");
        when(pageRepository.findByIssueKey("JEAP-1")).thenReturn(Optional.of(tracked));
        when(confluenceAdapter.updatePageById(eq("issue-page"), eq("project-page"), eq("JEAP-1"),
                any(), eq(true))).thenReturn(true);

        generator.generatePages("project-page", "JEAP", List.of("JEAP-1"));

        verify(confluenceAdapter, never()).addOrUpdatePageUnderAncestor(any(), any(), any());
        assertThat(tracked.getParentPageId()).isEqualTo("project-page");
    }

    @Test
    void doesNotTrackOrLinkWhenConfluenceFails() {
        when(pageRepository.findByIssueKey("JEAP-1")).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("project-page", "JEAP-1")).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(any(), any(), any()))
                .thenThrow(new IllegalStateException("unavailable"));

        List<String> issueKeys = List.of("JEAP-1");

        assertThatThrownBy(() -> generator.generatePages("project-page", "JEAP", issueKeys))
                .isInstanceOf(IllegalStateException.class);
        verify(pageRepository, never()).save(any());
        verifyNoInteractions(jiraAdapter);
    }
}
