package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageCleanupCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.StagingEnvironmentResolver;
import ch.admin.bit.jeap.deploymentlog.domain.StagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ComponentPageRepositoryImpl implements ComponentPageRepository {

    private final JpaComponentPageRepository repository;
    private final StagingEnvironmentResolver stageResolver;
    private final StagingProperties stagingProperties;

    @Override
    public Optional<ComponentPage> findByComponentId(UUID componentId) {
        return repository.findById(componentId);
    }

    @Override
    public ComponentPage save(ComponentPage componentPage) {
        return repository.save(componentPage);
    }

    @Override
    public List<ComponentPage> findByParentPageId(String parentPageId) {
        return repository.findByParentPageId(parentPageId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComponentPageCleanupCandidate> findCleanupCandidates(int limit) {
        if (!stagingProperties.isEnabled()) {
            return List.of();
        }
        return repository.findCleanupCandidates(relevantStageNames(), PageRequest.of(0, limit));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markCleanupAttemptedIfNoRelevantDeployment(UUID componentId, ZonedDateTime attemptedAt) {
        if (!stagingProperties.isEnabled()) {
            return 0;
        }
        return repository.markCleanupAttemptedIfNoRelevantDeployment(componentId, attemptedAt, relevantStageNames());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteIfNoRelevantDeployment(UUID componentId) {
        if (!stagingProperties.isEnabled()) {
            return 0;
        }
        return repository.deleteIfNoRelevantDeployment(componentId, relevantStageNames());
    }

    private List<String> relevantStageNames() {
        return stageResolver.relevantEnvironments().stream()
                .map(Environment::getName).toList();
    }
}
