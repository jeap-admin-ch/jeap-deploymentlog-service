package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class JiraIssuePageRepositoryImplTest {

    @Autowired private JiraIssuePageRepository issuePageRepository;
    @Autowired private JiraProjectPageRepository projectPageRepository;

    @Test
    void persistsIssueProjectAndPageLocationByIssueKey() {
        projectPageRepository.save(JiraProjectPage.create("JEAP", "project-page", "changes-page"));
        issuePageRepository.save(JiraIssuePage.create("JEAP-1", "JEAP", "issue-page", "project-page"));

        assertThat(issuePageRepository.findByIssueKey("JEAP-1"))
                .get()
                .extracting(JiraIssuePage::getProjectKey, JiraIssuePage::getPageId,
                        JiraIssuePage::getParentPageId)
                .containsExactly("JEAP", "issue-page", "project-page");
    }

    @Test
    void persistsThePageTheRemoteLinkWasWrittenFor() {
        projectPageRepository.save(JiraProjectPage.create("JEAP", "project-page", "changes-page"));
        JiraIssuePage page = JiraIssuePage.create("JEAP-1", "JEAP", "issue-page", "project-page");
        page.remoteLinkWritten();
        issuePageRepository.save(page);

        JiraIssuePage stored = issuePageRepository.findByIssueKey("JEAP-1").orElseThrow();
        assertThat(stored.getRemoteLinkPageId()).isEqualTo("issue-page");
        assertThat(stored.isRemoteLinkUpToDate()).isTrue();
    }
}
