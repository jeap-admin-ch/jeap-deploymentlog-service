package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArtifactVersionServiceTest {

    @Mock
    private ArtifactVersionRepository artifactVersionRepository;
    @InjectMocks
    private ArtifactVersionService artifactVersionService;
    @Captor
    ArgumentCaptor<ArtifactVersion> artifactVersionCaptor;

    @Test
    void saveArtifactVersion() {
        final UUID uuid = UUID.randomUUID();
        artifactVersionService.saveArtifactVersion(uuid, "test", "link");

        verify(artifactVersionRepository, times(1)).save(artifactVersionCaptor.capture());
        final ArtifactVersion value = artifactVersionCaptor.getValue();
        assertThat(value.getId()).isEqualTo(uuid);
        assertThat(value.getCoordinates()).isEqualTo("test");
        assertThat(value.getBuildJobLink()).isEqualTo("link");
        assertThat(value.getCreatedAt()).isNotNull();
    }

}
