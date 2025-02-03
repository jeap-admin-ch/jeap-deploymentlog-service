package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentPageRepository {

    DeploymentPage save(DeploymentPage deploymentPage);

    void delete(DeploymentPage deploymentPage);

    Optional<DeploymentPage> findDeploymentPageByDeploymentId(UUID deploymentId);

    /**
     * @return All deployment pages for a system for the given environments, results are sorted by deployment last modified date descending
     */
    List<DeploymentPage> getSystemDeploymentPagesForEnvironments(UUID systemId, List<Environment> envs);

    /**
     * @return All deployment ids and page ids for a system
     */
    List<DeploymentPageQueryResult> getDeploymentPagesForSystem(UUID systemId);
}
