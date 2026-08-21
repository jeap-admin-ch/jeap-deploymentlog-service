package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FlowRepositoryImpl implements FlowRepository {

    private final JpaFlowRepository jpaFlowRepository;
    private final JpaComponentRepository jpaComponentRepository;
    private final EntityManager entityManager;

    @Override
    public void lockComponent(UUID componentId) {
        jpaComponentRepository.lockById(componentId)
                .orElseThrow(() -> new IllegalStateException("Component not found while acquiring flow lock: " + componentId));
    }

    @Override
    public List<Flow> findOpenFlows(UUID componentId, String versionName) {
        return jpaFlowRepository.findByBusinessVersionAndState(componentId, versionName, FlowState.OPEN);
    }

    @Override
    public Optional<Flow> findByDeploymentId(UUID deploymentId) {
        return jpaFlowRepository.findByDeploymentId(deploymentId);
    }

    @Override
    public Flow save(Flow flow) {
        entityManager.persist(flow);
        return flow;
    }
}
