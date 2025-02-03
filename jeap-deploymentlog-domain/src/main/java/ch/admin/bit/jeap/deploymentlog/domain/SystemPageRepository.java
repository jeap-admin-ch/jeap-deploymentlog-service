package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface to be implemented by a persistence provider to access @{@link System}s
 */
public interface SystemPageRepository {

    SystemPage save(SystemPage systemPage);
    Optional<SystemPage> findSystemPageBySystemId(UUID systemId);
    void deleteSystemPage(SystemPage systemPage);

}
