package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlowRepository {

    void lockComponent(UUID componentId);

    List<Flow> findOpenFlows(UUID componentId, String versionName);

    Optional<Flow> findByDeploymentId(UUID deploymentId);

    Flow save(Flow flow);
}
