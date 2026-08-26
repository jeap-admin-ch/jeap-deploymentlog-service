package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroupRepository;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroupService;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ContextConfiguration(classes = {PersistenceConfiguration.class, SystemGroupService.class})
class SystemGroupRepositoryImplTest {

    @Autowired
    private SystemGroupRepository systemGroupRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private SystemGroupService systemGroupService;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndFindsGroupsInDeterministicOrder() {
        SystemGroup zebra = systemGroupService.create("Zebra");
        SystemGroup alpha = systemGroupService.create("alpha");

        assertThat(systemGroupRepository.findAllSorted())
                .extracting(SystemGroup::getId)
                .containsExactly(alpha.getId(), zebra.getId());
        assertThat(systemGroupRepository.findByNormalizedName("zebra"))
                .contains(zebra);
    }

    @Test
    void databaseConstraintRejectsCaseInsensitiveDuplicateNames() {
        systemGroupRepository.save(new SystemGroup("Border Control"));
        SystemGroup duplicate = new SystemGroup("border control");

        assertThatThrownBy(() -> systemGroupRepository.save(duplicate))
                .isInstanceOf(SystemGroupNameAlreadyExistsException.class);
    }

    @Test
    void persistsNamesLongerThanVarcharLimit() {
        String longName = "a".repeat(5_000);

        SystemGroup saved = systemGroupService.create("  " + longName + "  ");
        entityManager.clear();

        assertThat(systemGroupRepository.findById(saved.getId()))
                .get()
                .extracting(SystemGroup::getName)
                .isEqualTo(longName);
    }

    @Test
    void assigningAGroupReplacesThePreviousAssignment() {
        System system = systemRepository.save(new System("my-system"));
        SystemGroup first = systemGroupService.create("First");
        SystemGroup second = systemGroupService.create("Second");

        systemGroupService.assignSystem(first.getId(), system.getId());
        systemGroupService.assignSystem(second.getId(), system.getId());
        entityManager.flush();
        entityManager.clear();

        System reloaded = systemRepository.findById(system.getId()).orElseThrow();
        assertThat(reloaded.getSystemGroup().getId()).isEqualTo(second.getId());
        assertThat(systemGroupRepository.findById(first.getId()).orElseThrow().getSystems()).isEmpty();
        assertThat(systemGroupRepository.findById(second.getId()).orElseThrow().getSystems())
                .extracting(System::getId)
                .containsExactly(system.getId());
    }

    @Test
    void deletingAGroupKeepsItsSystemsUngrouped() {
        System system = systemRepository.save(new System("my-system"));
        SystemGroup group = systemGroupService.create("Group");
        systemGroupService.assignSystem(group.getId(), system.getId());

        systemGroupService.delete(group.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(systemGroupRepository.findById(group.getId())).isEmpty();
        assertThat(systemRepository.findById(system.getId()))
                .get()
                .extracting(System::getSystemGroup)
                .isNull();
    }
}
