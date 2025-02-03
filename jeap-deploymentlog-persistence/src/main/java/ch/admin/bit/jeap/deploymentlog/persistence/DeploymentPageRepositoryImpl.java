package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageQueryResult;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeploymentPageRepositoryImpl implements DeploymentPageRepository {

    private final JpaDeploymentPageRepository jpaDeploymentPageRepository;

    @Override
    public DeploymentPage save(DeploymentPage deploymentPage) {
        return jpaDeploymentPageRepository.save(deploymentPage);
    }

    @Override
    public void delete(DeploymentPage deploymentPage) {
        jpaDeploymentPageRepository.delete(deploymentPage);
    }

    @Override
    public Optional<DeploymentPage> findDeploymentPageByDeploymentId(UUID deploymentId) {
        return jpaDeploymentPageRepository.findDeploymentPageByDeploymentId(deploymentId);
    }

    @Override
    public List<DeploymentPage> getSystemDeploymentPagesForEnvironments(UUID systemId, List<Environment> envs) {
        return jpaDeploymentPageRepository.getDeploymentPagesForEnvironments(envs, systemId);
    }

    @Override
    public List<DeploymentPageQueryResult> getDeploymentPagesForSystem(UUID systemId) {
        return jpaDeploymentPageRepository.getDeploymentPagesForSystem(systemId);
    }
}
