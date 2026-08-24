package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaComponentRepository extends CrudRepository<Component, UUID> {

    @Query(value = "select id from component where id = :id for update", nativeQuery = true)
    Optional<UUID> lockById(@Param("id") UUID id);

    @Query(value = """
            select component.id
            from component
            where component.id = (
                select component_version.component_id
                from component_version
                join flow on flow.component_version_id = component_version.id
                join deployment on deployment.flow_id = flow.id
                where deployment.id = :deploymentId
            )
            for update
            """, nativeQuery = true)
    Optional<UUID> lockByDeploymentId(@Param("deploymentId") UUID deploymentId);
}
