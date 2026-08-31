package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class ComponentPageRepositoryImplTest {

    @Autowired
    private ComponentPageRepository componentPageRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsTrackingByTechnicalComponentIdAndUpdatesItsLocation() {
        Component component = component("component", "system");
        ComponentPage page = componentPageRepository.save(
                ComponentPage.create(component.getId(), "page-id", "components-old"));

        page.updateLocation("page-id", "components-new");
        componentPageRepository.save(page);
        entityManager.flush();
        entityManager.clear();

        assertThat(componentPageRepository.findByComponentId(component.getId()))
                .get()
                .satisfies(persisted -> {
                    assertThat(persisted.getPageId()).isEqualTo("page-id");
                    assertThat(persisted.getParentPageId()).isEqualTo("components-new");
                });
    }

    @Test
    void pageIdIsUniqueAcrossComponents() {
        Component first = component("first", "first-system");
        Component second = component("second", "second-system");
        componentPageRepository.save(ComponentPage.create(first.getId(), "same-page", "components-first"));
        componentPageRepository.save(ComponentPage.create(second.getId(), "same-page", "components-second"));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
    }

    private Component component(String componentName, String systemName) {
        System system = systemRepository.save(new System(systemName));
        return componentRepository.save(new Component(componentName, system));
    }
}
