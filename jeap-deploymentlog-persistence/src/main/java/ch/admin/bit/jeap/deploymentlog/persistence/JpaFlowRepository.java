package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JpaFlowRepository extends CrudRepository<Flow, UUID> {

    @Query("""
            select flow from Flow flow
            where flow.componentVersion.component.id = :componentId
            and flow.componentVersion.versionName = :versionName
            and flow.state = :state
            """)
    List<Flow> findByBusinessVersionAndState(@Param("componentId") UUID componentId,
                                             @Param("versionName") String versionName,
                                             @Param("state") FlowState state);

    @Query("select deployment.flow from Deployment deployment where deployment.id = :deploymentId")
    Optional<Flow> findByDeploymentId(@Param("deploymentId") UUID deploymentId);
}
