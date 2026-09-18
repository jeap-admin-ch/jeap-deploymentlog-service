package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComponentPageRepository {

    Optional<ComponentPage> findByComponentId(UUID componentId);

    ComponentPage save(ComponentPage componentPage);

    List<ComponentPage> findByParentPageId(String parentPageId);

    List<ComponentPageCleanupCandidate> findCleanupCandidates(int limit);

    int markCleanupAttemptedIfNoCodeDeployment(UUID componentId, ZonedDateTime attemptedAt);

    int deleteIfNoCodeDeployment(UUID componentId);
}
