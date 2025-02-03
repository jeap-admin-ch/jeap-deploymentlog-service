package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentListPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentListPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentListPageRepositoryImpl implements DeploymentListPageRepository {

    private final JpaDeploymentListPageRepository jpaDeploymentListPageRepository;

    @Override
    public DeploymentListPage save(DeploymentListPage deploymentListPage) {
        return jpaDeploymentListPageRepository.save(deploymentListPage);
    }

    @Override
    public Optional<DeploymentListPage> findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(UUID systemId, UUID environmentId, int year) {
        return jpaDeploymentListPageRepository.findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(systemId, environmentId, year);
    }

    @Override
    public void deleteDeploymentListPageBySystemId(UUID systemId) {
        jpaDeploymentListPageRepository.deleteDeploymentListPageBySystemId(systemId);
    }

}
