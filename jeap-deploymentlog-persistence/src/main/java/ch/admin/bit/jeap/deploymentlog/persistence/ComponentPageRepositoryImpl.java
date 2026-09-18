package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageCleanupCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
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
        return repository.findCleanupCandidates(PageRequest.of(0, limit));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markCleanupAttemptedIfNoCodeDeployment(UUID componentId, ZonedDateTime attemptedAt) {
        return repository.markCleanupAttemptedIfNoCodeDeployment(componentId, attemptedAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteIfNoCodeDeployment(UUID componentId) {
        return repository.deleteIfNoCodeDeployment(componentId);
    }
}
