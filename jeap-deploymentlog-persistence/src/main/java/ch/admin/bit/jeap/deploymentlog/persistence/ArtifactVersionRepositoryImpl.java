package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersion;
import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArtifactVersionRepositoryImpl implements ArtifactVersionRepository {

    private final JpaArtifactVersionRepository jpaArtifactVersionRepository;

    @Override
    public ArtifactVersion save(ArtifactVersion artifactVersion) {
        return jpaArtifactVersionRepository.save(artifactVersion);
    }

    @Override
    public List<ArtifactVersion> findAllByCoordinates(String coordinates){
        return jpaArtifactVersionRepository.findAllByCoordinates(coordinates);
    }
    @Override
    public Optional<ArtifactVersion> findById(UUID id){
        return jpaArtifactVersionRepository.findById(id);
    }

}
