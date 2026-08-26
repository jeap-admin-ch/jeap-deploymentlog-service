package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemGroupRepository {

    SystemGroup save(SystemGroup systemGroup);

    Optional<SystemGroup> findById(UUID id);

    Optional<SystemGroup> findByNormalizedName(String normalizedName);

    List<SystemGroup> findAllSorted();

    void delete(SystemGroup systemGroup);
}
