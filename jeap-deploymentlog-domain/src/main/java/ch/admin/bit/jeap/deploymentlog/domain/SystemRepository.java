package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface to be implemented by a persistence provider to access @{@link System}s
 */
public interface SystemRepository {

    Optional<System> findByNameIgnoreCase(String name);

    System getById(UUID systemId);

    System save(System system);

    List<System> findAll();

    List<UUID> getAllSystemIds();

    void delete(System system);
}
