package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface to be implemented by a persistence provider to access @{@link ArtifactVersion}s
 */
public interface ArtifactVersionRepository {

    Optional<ArtifactVersion> findById(UUID id);

    ArtifactVersion save(ArtifactVersion artifactVersion);

    List<ArtifactVersion> findAllByCoordinates(String coordinates);

}
