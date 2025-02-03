package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;

/**
 * Interface to be implemented by a persistence provider to access @{@link SystemAlias}es
 */
public interface SystemAliasRepository {

    SystemAlias save(SystemAlias systemAlias);

    Optional<SystemAlias> findByName(String name);
}
