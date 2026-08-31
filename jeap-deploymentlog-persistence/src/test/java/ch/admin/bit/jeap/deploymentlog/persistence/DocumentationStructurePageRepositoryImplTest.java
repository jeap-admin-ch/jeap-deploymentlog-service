package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DocumentationStructurePage;
import ch.admin.bit.jeap.deploymentlog.domain.DocumentationStructurePageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class DocumentationStructurePageRepositoryImplTest {

    @Autowired
    private DocumentationStructurePageRepository repository;

    @Test
    void persistsAndUpdatesStablePageTracking() {
        DocumentationStructurePage page = DocumentationStructurePage.create(
                "TOP:SYSTEMS", "systems-page", "root-page");
        repository.save(page);

        DocumentationStructurePage persisted = repository.findByStructureKey("TOP:SYSTEMS").orElseThrow();
        assertThat(persisted.getPageId()).isEqualTo("systems-page");
        assertThat(persisted.getParentPageId()).isEqualTo("root-page");

        persisted.updateLocation("systems-page", "new-root-page");
        repository.save(persisted);

        assertThat(repository.findByStructureKey("TOP:SYSTEMS"))
                .get()
                .extracting(DocumentationStructurePage::getParentPageId)
                .isEqualTo("new-root-page");
    }

    @Test
    void deletesTrackingWithoutDeletingAnyDomainObject() {
        DocumentationStructurePage page = repository.save(DocumentationStructurePage.create(
                "SYSTEM_GROUP:group-id", "group-page", "systems-page"));

        repository.delete(page);

        assertThat(repository.findAll()).isEmpty();
    }
}
