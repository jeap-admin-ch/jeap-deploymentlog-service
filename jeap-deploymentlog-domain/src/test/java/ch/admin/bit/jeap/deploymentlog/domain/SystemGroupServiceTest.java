package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundForGroupException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidSystemGroupNameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemGroupServiceTest {

    @Mock
    private SystemGroupRepository systemGroupRepository;
    @Mock
    private SystemRepository systemRepository;
    @InjectMocks
    private SystemGroupService systemGroupService;

    @Test
    void createSanitizesName() {
        when(systemGroupRepository.findByNormalizedName("example group")).thenReturn(Optional.empty());
        when(systemGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SystemGroup group = systemGroupService.create("  Example Group  ");

        assertThat(group.getId()).isNotNull();
        assertThat(group.getName()).isEqualTo("Example Group");
        assertThat(group.getNormalizedName()).isEqualTo("example group");
    }

    @Test
    void createRejectsCaseInsensitiveDuplicate() {
        SystemGroup existing = new SystemGroup("Example Group");
        when(systemGroupRepository.findByNormalizedName("example group")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> systemGroupService.create(" example GROUP "))
                .isInstanceOf(SystemGroupNameAlreadyExistsException.class);

        verify(systemGroupRepository, never()).save(any());
    }

    @Test
    void createRejectsBlankNameWithDomainException() {
        assertThatThrownBy(() -> systemGroupService.create("   "))
                .isInstanceOf(InvalidSystemGroupNameException.class);

        verify(systemGroupRepository, never()).save(any());
    }

    @Test
    void renamePreservesIdAndAssignments() {
        SystemGroup group = new SystemGroup("Old name");
        System system = new System("my-system");
        system.assignToSystemGroup(group);
        when(systemGroupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(systemGroupRepository.findByNormalizedName("new name")).thenReturn(Optional.empty());
        when(systemGroupRepository.save(group)).thenReturn(group);

        SystemGroup renamed = systemGroupService.rename(group.getId(), " New name ");

        assertThat(renamed.getId()).isEqualTo(group.getId());
        assertThat(renamed.getName()).isEqualTo("New name");
        assertThat(renamed.getSystems()).containsExactly(system);
    }

    @Test
    void assignReplacesPreviousGroupAndIsIdempotent() {
        SystemGroup oldGroup = new SystemGroup("Old");
        SystemGroup newGroup = new SystemGroup("New");
        System system = new System("my-system");
        system.assignToSystemGroup(oldGroup);
        when(systemGroupRepository.findByIdForUpdate(newGroup.getId())).thenReturn(Optional.of(newGroup));
        when(systemRepository.findByNameIgnoreCase("MY-SYSTEM")).thenReturn(Optional.of(system));

        systemGroupService.assignSystem(newGroup.getId(), "MY-SYSTEM");
        systemGroupService.assignSystem(newGroup.getId(), "MY-SYSTEM");

        assertThat(system.getSystemGroup()).isSameAs(newGroup);
        assertThat(oldGroup.getSystems()).isEmpty();
        assertThat(newGroup.getSystems()).containsExactly(system);
    }

    @Test
    void assignmentIsIdempotentForDifferentGroupInstanceWithSameId() {
        SystemGroup assignedGroup = new SystemGroup("Assigned");
        SystemGroup samePersistedGroup = mock(SystemGroup.class);
        when(samePersistedGroup.getId()).thenReturn(assignedGroup.getId());
        System system = new System("my-system");
        system.assignToSystemGroup(assignedGroup);

        system.assignToSystemGroup(samePersistedGroup);

        assertThat(system.getSystemGroup()).isSameAs(assignedGroup);
        assertThat(assignedGroup.getSystems()).containsExactly(system);
    }

    @Test
    void removeOnlyRemovesAssignmentToRequestedGroupAndIsIdempotent() {
        SystemGroup assignedGroup = new SystemGroup("Assigned");
        SystemGroup otherGroup = new SystemGroup("Other");
        System system = new System("my-system");
        system.assignToSystemGroup(assignedGroup);
        when(systemRepository.findByNameIgnoreCase(system.getName())).thenReturn(Optional.of(system));
        when(systemGroupRepository.findByIdForUpdate(otherGroup.getId())).thenReturn(Optional.of(otherGroup));
        when(systemGroupRepository.findByIdForUpdate(assignedGroup.getId())).thenReturn(Optional.of(assignedGroup));

        systemGroupService.removeSystem(otherGroup.getId(), system.getName());
        assertThat(system.getSystemGroup()).isSameAs(assignedGroup);

        systemGroupService.removeSystem(assignedGroup.getId(), system.getName());
        systemGroupService.removeSystem(assignedGroup.getId(), system.getName());
        assertThat(system.getSystemGroup()).isNull();
        assertThat(assignedGroup.getSystems()).isEmpty();
    }

    @Test
    void deleteDelegatesToRepository() {
        SystemGroup group = new SystemGroup("Group");
        when(systemGroupRepository.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));

        systemGroupService.delete(group.getId());

        verify(systemGroupRepository).delete(group);
    }

    @Test
    void associationRequiresExistingGroupAndSystem() {
        UUID unknownGroupId = UUID.randomUUID();
        String arbitrarySystemName = "arbitrary-system";
        when(systemGroupRepository.findByIdForUpdate(unknownGroupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemGroupService.assignSystem(unknownGroupId, arbitrarySystemName))
                .isInstanceOf(SystemGroupNotFoundException.class);

        SystemGroup group = new SystemGroup("Group");
        UUID groupId = group.getId();
        String unknownSystemName = "unknown-system";
        when(systemGroupRepository.findByIdForUpdate(groupId)).thenReturn(Optional.of(group));
        when(systemRepository.findByNameIgnoreCase(unknownSystemName)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemGroupService.assignSystem(groupId, unknownSystemName))
                .isInstanceOf(SystemNotFoundForGroupException.class);
    }
}
