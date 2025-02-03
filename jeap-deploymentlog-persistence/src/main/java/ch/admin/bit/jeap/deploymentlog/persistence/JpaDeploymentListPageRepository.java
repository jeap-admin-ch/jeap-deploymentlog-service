package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentListPage;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaDeploymentListPageRepository extends CrudRepository<DeploymentListPage, UUID> {

    Optional<DeploymentListPage> findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(UUID systemId, UUID environmentId, int year);
    void deleteDeploymentListPageBySystemId(UUID systemId);

}
