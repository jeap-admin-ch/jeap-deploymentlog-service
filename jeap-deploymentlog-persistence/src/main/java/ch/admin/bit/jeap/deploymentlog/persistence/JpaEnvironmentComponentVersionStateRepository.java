package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersionSummary;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentComponentVersionState;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
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
            and s.id = :systemId \
            and ecvs.deployment.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
            """)
    List<Component> findComponentsBySystemId(@Param("systemId") UUID systemId);

    Optional<EnvironmentComponentVersionState> findByEnvironmentAndComponent(Environment environment, Component component);

    List<EnvironmentComponentVersionState> findByComponentIn(Set<Component> components);

    @Query("""
            select ecvs.component.name as componentName, (case when ecvs.deployment.sequence = ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED \
                         then null else ecvs.componentVersion.versionName end) as version, \
                   (ecvs.deployment.sequence = ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED) as undeployed, \
                   (case when ecvs.deployment.sequence = ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED \
                         then ecvs.deployment.startedAt end) as undeployedAt \
            from EnvironmentComponentVersionState ecvs \
            where ecvs.environment.id = :environmentId \
            and (:includeUndeployed = true or ecvs.deployment.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED)
            """)
    List<ComponentVersionSummary> getDeployedComponentsOnEnvironment(@Param("environmentId") UUID environmentId, @Param("includeUndeployed") boolean includeUndeployed);

    // Matched on the deployment the row points at when the statement runs, so a row a concurrent redeploy has
    // just pointed at a new deployment is no longer a tombstone and is left alone.
    @Modifying
    @Query("""
            delete from EnvironmentComponentVersionState ecvs \
            where ecvs.deployment.id in ( \
                select d.id from Deployment d \
                where d.sequence = ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED \
                and d.startedAt < :undeployedBefore)
            """)
    int deleteUndeployedBefore(@Param("undeployedBefore") ZonedDateTime undeployedBefore);
}
