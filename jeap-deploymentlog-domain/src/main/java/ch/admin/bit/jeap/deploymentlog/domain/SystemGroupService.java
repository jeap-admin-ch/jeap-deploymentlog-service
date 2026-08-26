package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundByIdException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SystemGroupService {

    private final SystemGroupRepository systemGroupRepository;
    private final SystemRepository systemRepository;

    @Transactional
    public SystemGroup create(String name) {
        SystemGroupName groupName = SystemGroupName.of(name);
        rejectDuplicateName(groupName, null);
        return systemGroupRepository.save(new SystemGroup(groupName));
    }

    @TransactionalReadReplica
    public List<SystemGroup> findAll() {
        return systemGroupRepository.findAllSorted();
    }

    @TransactionalReadReplica
    public SystemGroup get(UUID groupId) {
        return getExistingGroup(groupId);
    }

    @Transactional
    public SystemGroup rename(UUID groupId, String name) {
        SystemGroup group = getExistingGroup(groupId);
        SystemGroupName groupName = SystemGroupName.of(name);
        rejectDuplicateName(groupName, groupId);
        group.rename(groupName);
        return systemGroupRepository.save(group);
    }

    @Transactional
    public void delete(UUID groupId) {
        SystemGroup group = getExistingGroup(groupId);
        for (System system : List.copyOf(group.getSystems())) {
            system.assignToSystemGroup(null);
        }
        systemGroupRepository.delete(group);
    }

    @Transactional
    public void assignSystem(UUID groupId, UUID systemId) {
        SystemGroup group = getExistingGroup(groupId);
        System system = getExistingSystem(systemId);
        system.assignToSystemGroup(group);
    }

    @Transactional
    public void removeSystem(UUID groupId, UUID systemId) {
        SystemGroup group = getExistingGroup(groupId);
        System system = getExistingSystem(systemId);
        if (system.getSystemGroup() != null
                && Objects.equals(system.getSystemGroup().getId(), group.getId())) {
            system.assignToSystemGroup(null);
        }
    }

    private SystemGroup getExistingGroup(UUID groupId) {
        return systemGroupRepository.findById(groupId)
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
