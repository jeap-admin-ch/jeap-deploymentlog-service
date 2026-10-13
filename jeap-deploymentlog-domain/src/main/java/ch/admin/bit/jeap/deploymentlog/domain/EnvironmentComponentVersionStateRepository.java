package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interface to be implemented by a persistence provider to access @{@link EnvironmentComponentVersionState}s
 */
public interface EnvironmentComponentVersionStateRepository {

    /**
     * Get all Compenents of a System, which have Successful-Deployments. A component undeployed from every
     * environment it was deployed to is left out.
     * @param system as System
     * @return List of Componentsd
     */
    List<Component> findComponentsBySystem(System system);

    Optional<EnvironmentComponentVersionState> findByEnvironmentAndComponent(Environment environment, Component component);

    List<EnvironmentComponentVersionState> findByComponentIn(Set<Component> components);

    EnvironmentComponentVersionState save(EnvironmentComponentVersionState environmentComponentVersionState);

    /**
     * Component version summaries of what is deployed on an environment. Undeployed components are excluded
     * unless {@code includeUndeployed} is set, which also fills in {@link ComponentVersionSummary#getUndeployed()}
     * and {@link ComponentVersionSummary#getUndeployedAt()}.
     */
    List<ComponentVersionSummary> getDeployedComponentsOnEnvironment(Environment environmentName, boolean includeUndeployed);

    /**
     * Deletes the tombstones of components undeployed before the given instant, so they are no longer reported
     * as undeployed at all. Returns the number of tombstones deleted.
     */
    int deleteUndeployedBefore(ZonedDateTime undeployedBefore);
}
