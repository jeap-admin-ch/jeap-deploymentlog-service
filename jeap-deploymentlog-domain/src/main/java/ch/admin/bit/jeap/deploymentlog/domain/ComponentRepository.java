package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;
import java.util.Optional;

/**
 * Interface to be implemented by a persistence provider to access @{@link Component}s
 */
public interface ComponentRepository {

    Component save(Component component);

    void lockById(UUID componentId);

    Optional<Component> findById(UUID componentId);
}
