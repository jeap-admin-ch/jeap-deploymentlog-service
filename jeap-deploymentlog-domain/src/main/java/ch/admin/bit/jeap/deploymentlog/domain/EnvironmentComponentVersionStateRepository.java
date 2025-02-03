package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface to be implemented by a persistence provider to access @{@link EnvironmentComponentVersionState}s
 */
public interface EnvironmentComponentVersionStateRepository {

    /**
     * Get all Compenents of a System, which have Successful-Deployments
     * @param system as System
     * @return List of Componentsd
     */
    List<Component> findComponentsBySystem(System system);

    Optional<EnvironmentComponentVersionState> findByEnvironmentAndComponent(Environment environment, Component component);

    List<EnvironmentComponentVersionState> findByComponentIn(Set<Component> components);

    EnvironmentComponentVersionState save(EnvironmentComponentVersionState environmentComponentVersionState);

    void deleteByComponentEqualsAndEnvironmentEquals(Component component, Environment environment);

    List<ComponentVersionSummary> getDeployedComponentsOnEnvironment(Environment environmentName);
}
