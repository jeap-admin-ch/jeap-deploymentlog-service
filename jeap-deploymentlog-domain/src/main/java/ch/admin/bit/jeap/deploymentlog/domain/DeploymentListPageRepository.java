package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;
import java.util.UUID;

public interface DeploymentListPageRepository {

    DeploymentListPage save(DeploymentListPage deploymentListPage);
    Optional<DeploymentListPage> findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(UUID systemId, UUID environmentId, int year);
    void deleteDeploymentListPageBySystemId(UUID systemId);

}
