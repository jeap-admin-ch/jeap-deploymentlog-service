package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class JiraProjectPageRepositoryImplTest {

    @Autowired
    private JiraProjectPageRepository repository;

    @Test
    void persistsAndUpdatesPageTrackingByNormalizedProjectKey() {
        JiraProjectPage page = repository.save(JiraProjectPage.create("JEAP", "page-1", "changes-page"));

        assertThat(repository.findByProjectKey("JEAP"))
                .get()
                .extracting(JiraProjectPage::getPageId, JiraProjectPage::getParentPageId)
                .containsExactly("page-1", "changes-page");

        page.updateLocation("page-1", "new-changes-page");
        repository.save(page);

        assertThat(repository.findByProjectKey("JEAP"))
                .get()
                .extracting(JiraProjectPage::getParentPageId)
                .isEqualTo("new-changes-page");
    }
}
