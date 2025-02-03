package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersion;
import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class ArtifactVersionRepositoryImplTest {

    @Autowired
    private ArtifactVersionRepository artifactVersionRepository;

    @Test
    void findById_artifactVersionFound() {
        ArtifactVersion artifactVersion = ArtifactVersion.builder()
                .id(UUID.randomUUID())
                .coordinates("test")
                .buildJobLink("link")
                .build();
        artifactVersionRepository.save(artifactVersion);

        Optional<ArtifactVersion> artifactVersionOptional = artifactVersionRepository.findById(artifactVersion.getId());
        assertThat(artifactVersionOptional).isPresent();
        assertThat(artifactVersionOptional.get().getCoordinates()).isEqualTo(artifactVersion.getCoordinates());
        assertThat(artifactVersionOptional.get().getBuildJobLink()).isEqualTo(artifactVersion.getBuildJobLink());
    }

    @Test
    void findAllByCoordinates_artifactVersionFound() {
        ArtifactVersion artifactVersion = ArtifactVersion.builder()
                .id(UUID.randomUUID())
                .coordinates("test")
                .buildJobLink("link")
                .build();
        artifactVersionRepository.save(artifactVersion);

        List<ArtifactVersion> artifactVersions = artifactVersionRepository.findAllByCoordinates(artifactVersion.getCoordinates());
        assertThat(artifactVersions).hasSize(1);
        assertThat(artifactVersions.get(0).getCoordinates()).isEqualTo(artifactVersion.getCoordinates());
        assertThat(artifactVersions.get(0).getBuildJobLink()).isEqualTo(artifactVersion.getBuildJobLink());
    }

}
