package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Version membership is intrinsic to the deployment; only relevant stages are classified. */
class DeploymentStagingTargetsTest extends StagingTestSupport {
    @Test void retainsAllExplicitTargetsNormalized() {
        Deployment d = deployment(ref);
        service.prepare(d, List.of(" abn ", "prod"));
        assertThat(d.getFinalDeploymentEnvironments()).containsExactlyInAnyOrder("ABN", "PROD");
        verify(repository).lockComponent(component.getId());
    }
    @Test void omittedTargetsDoNotImplyAutoStaging() {
        Deployment d = deployment(ref);
        service.prepare(d, null);
        assertThat(d.getFinalDeploymentEnvironments()).isEmpty();
        assertThat(d.getStagingType()).isEqualTo(DeploymentStagingType.NEW);
    }
    @Test void ignoresStagesOutsideConfiguredRange() {
        Deployment d = deployment(new Environment("DEV"));
        service.prepare(d, List.of());
        assertThat(d.getStagingType()).isNull();
        verifyNoInteractions(repository);
    }
    @Test void disabledTrackingDoesNotClassifyDeployments() {
        properties.setEnabled(false);
        Deployment d = deployment(ref);
        service.prepare(d, List.of("PROD"));
        assertThat(d.getStagingType()).isNull();
        assertThat(d.getFinalDeploymentEnvironments()).containsExactly("PROD");
        verifyNoInteractions(repository);
    }
    @Test void configurationOnlyDeploymentDoesNotResolveStagingConfiguration() {
        Deployment d = deployment(new Environment("CONFIG-ENV"));
        d.getDeploymentTypes().clear();
        d.getDeploymentTypes().add(DeploymentType.CONFIG);
        service.prepare(d, List.of("PROD"));
        assertThat(d.getStagingType()).isNull();
        assertThat(d.getFinalDeploymentEnvironments()).containsExactly("PROD");
        verifyNoInteractions(repository, resolver);
    }
}
