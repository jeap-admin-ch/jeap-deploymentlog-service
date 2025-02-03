package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersionSummary;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentComponentVersionState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JpaEnvironmentComponentVersionStateRepository extends CrudRepository<EnvironmentComponentVersionState, UUID> {

    @Query("""
            select distinct c from EnvironmentComponentVersionState ecvs, Component c, System s \
            where ecvs.component.id = c.id \
            and c.system.id = s.id \
            and s.id = :systemId
            """)
    List<Component> findComponentsBySystemId(@Param("systemId") UUID systemId);

    Optional<EnvironmentComponentVersionState> findByEnvironmentAndComponent(Environment environment, Component component);

    List<EnvironmentComponentVersionState> findByComponentIn(Set<Component> components);

    void deleteByComponentEqualsAndEnvironmentEquals(Component component, Environment environment);

    @Query(nativeQuery = true,
            value = """
                    select c.name as componentName, cv.version_name as version from environment_component_version_state ecvs \
                    inner join component_version cv on cv.id = ecvs.component_version_id \
                    inner join component c ON c.id = ecvs.component_id \
                    where ecvs.environment_id = :environmentId
                    """
    )
    List<ComponentVersionSummary> getDeployedComponentsOnEnvironment(@Param("environmentId") UUID environmentId);
}
