package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlowRepository {

    void lockComponent(UUID componentId);

    List<Flow> findOpenFlows(UUID componentId, String versionName);

    List<Flow> findOlderOpenFlows(UUID componentId, ZonedDateTime committedBefore, UUID excludedFlowId);

    Optional<Flow> findByDeploymentIdAndLockComponent(UUID deploymentId);

    Optional<Flow> findByDeploymentId(UUID deploymentId);

    Flow save(Flow flow);
}
