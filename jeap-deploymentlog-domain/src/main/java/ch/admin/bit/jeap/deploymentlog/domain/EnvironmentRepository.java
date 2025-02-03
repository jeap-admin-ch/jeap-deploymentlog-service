package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface to be implemented by a persistence provider to access @{@link Environment}s
 */
public interface EnvironmentRepository {

    Environment getById(UUID envId);

    Optional<Environment> findByName(String name);

    Environment save(Environment environment);

    List<Environment> findEnvironmentsForSystem(System system);

    List<Environment> findNonProductiveEnvironmentsForSystemId(UUID systemId);

    Iterable<Environment> findAll();
}
