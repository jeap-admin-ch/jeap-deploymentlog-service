package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.db.tx.TransactionalReadReplica;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactVersionService {

    private final ArtifactVersionRepository artifactVersionRepository;

    @TransactionalReadReplica
    public Optional<ArtifactVersion> findById(UUID id) {
        log.debug("Find the artifactVersion with id '{}'", id);
        return artifactVersionRepository.findById(id);
    }

    @Transactional
    public void saveArtifactVersion(UUID artifactVersionId, String coordinates, String buildJobLink) {
        final ArtifactVersion artifactVersion = ArtifactVersion.builder()
                .id(artifactVersionId)
                .coordinates(coordinates)
                .buildJobLink(buildJobLink)
                .build();
        artifactVersionRepository.save(artifactVersion);
    }

}
