package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundByIdException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemGroupService {

    private final SystemGroupRepository systemGroupRepository;
    private final SystemRepository systemRepository;

    @Transactional
    public SystemGroup create(String name) {
        SystemGroupName groupName = SystemGroupName.of(name);
        rejectDuplicateName(groupName, null);
        SystemGroup group = systemGroupRepository.save(new SystemGroup(groupName));
        log.info("Created system group {}", group.getId());
        return group;
    }

    @TransactionalReadReplica
    public List<SystemGroup> findAll() {
        return systemGroupRepository.findAllSortedWithSystems();
    }

    @TransactionalReadReplica
    public SystemGroup get(UUID groupId) {
        return getExistingGroup(groupId);
    }

    @Transactional
    public SystemGroup rename(UUID groupId, String name) {
        SystemGroup group = getExistingGroupForUpdate(groupId);
        SystemGroupName groupName = SystemGroupName.of(name);
        rejectDuplicateName(groupName, groupId);
        group.rename(groupName);
        SystemGroup renamedGroup = systemGroupRepository.save(group);
        log.info("Renamed system group {}", groupId);
        return renamedGroup;
    }

    @Transactional
    public void delete(UUID groupId) {
        SystemGroup group = getExistingGroupForUpdate(groupId);
        systemGroupRepository.delete(group);
        log.info("Deleted system group {}", groupId);
    }

    @Transactional
    public void assignSystem(UUID groupId, UUID systemId) {
        SystemGroup group = getExistingGroupForUpdate(groupId);
        System system = getExistingSystem(systemId);
        system.assignToSystemGroup(group);
        log.info("Assigned system {} to system group {}", systemId, groupId);
    }

    @Transactional
    public void removeSystem(UUID groupId, UUID systemId) {
        SystemGroup group = getExistingGroupForUpdate(groupId);
        System system = getExistingSystem(systemId);
        if (system.getSystemGroup() != null
                && Objects.equals(system.getSystemGroup().getId(), group.getId())) {
            system.assignToSystemGroup(null);
            log.info("Removed system {} from system group {}", systemId, groupId);
        }
    }

    private SystemGroup getExistingGroup(UUID groupId) {
        return systemGroupRepository.findByIdWithSystems(groupId)
                .orElseThrow(() -> new SystemGroupNotFoundException(groupId));
    }

    /**
     * All group mutations acquire the lock for exactly one target group before loading or changing a system.
     * Keeping this lock order consistent serializes delete and assignment operations without cross-group deadlocks.
     */
    private SystemGroup getExistingGroupForUpdate(UUID groupId) {
        return systemGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new SystemGroupNotFoundException(groupId));
    }

    private System getExistingSystem(UUID systemId) {
        return systemRepository.findById(systemId)
                .orElseThrow(() -> new SystemNotFoundByIdException(systemId));
    }

    private void rejectDuplicateName(SystemGroupName name, UUID ownGroupId) {
        systemGroupRepository.findByNormalizedName(name.normalized())
                .filter(existing -> !existing.getId().equals(ownGroupId))
                .ifPresent(existing -> {
                    throw new SystemGroupNameAlreadyExistsException(name.value());
                });
    }
}
