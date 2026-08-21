package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowTypeClassifierTest {

    @Mock
    private EnvironmentComponentVersionStateRepository environmentStateRepository;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private FlowStageResolver flowStageResolver;

    private FlowTypeClassifier classifier;
    private Environment dev;
    private Environment ref;
    private Component component;

    @BeforeEach
    void setUp() {
        classifier = new FlowTypeClassifier(environmentStateRepository, deploymentRepository, flowStageResolver);
        dev = new Environment("DEV");
        ref = new Environment("REF");
        component = new Component("service", new System("SYSTEM"));
    }

    @Test
    void classifiesRetryWhenVersionIsCurrentlyDeployed() {
        Deployment deployment = deployment("current", "1.0.0", ref);
        EnvironmentComponentVersionState state = EnvironmentComponentVersionState.fromDeployment(deployment);
        when(environmentStateRepository.findByEnvironmentAndComponent(ref, component)).thenReturn(Optional.of(state));

        assertThat(classifier.classify(deployment)).isEqualTo(FlowType.RETRY);
        verify(deploymentRepository, never()).getLastDeploymentForBusinessVersion(component, ref, "1.0.0",
                deployment.getId());
    }

    @Test
    void classifiesRetryBeforeRollbackWhenLastAttemptFailed() {
        Deployment deployment = deployment("current", "1.0.0", ref);
        Deployment failed = deployment("failed", "1.0.0", ref);
        failed.failed(ZonedDateTime.now(), "failed");
        when(environmentStateRepository.findByEnvironmentAndComponent(ref, component)).thenReturn(Optional.empty());
        when(deploymentRepository.getLastDeploymentForBusinessVersion(component, ref, "1.0.0", deployment.getId()))
                .thenReturn(Optional.of(failed));

        assertThat(classifier.classify(deployment)).isEqualTo(FlowType.RETRY);
        verify(deploymentRepository, never()).hasSuccessfulDeploymentForBusinessVersion(component, ref, "1.0.0",
                deployment.getId());
    }

    @Test
    void classifiesRollbackWhenVersionWasPreviouslySuccessful() {
        Deployment deployment = deployment("current", "1.0.0", ref);
        when(deploymentRepository.hasSuccessfulDeploymentForBusinessVersion(component, ref, "1.0.0",
                deployment.getId())).thenReturn(true);

        assertThat(classifier.classify(deployment)).isEqualTo(FlowType.ROLLBACK);
    }

    @Test
    void classifiesNewOnConfiguredStartEnvironment() {
        Deployment deployment = deployment("current", "1.0.0", dev);
        when(flowStageResolver.resolveStartEnvironment()).thenReturn(dev);

        assertThat(classifier.classify(deployment)).isEqualTo(FlowType.NEW);
    }

    @Test
    void classifiesAdHocOutsideConfiguredStartEnvironment() {
        Deployment deployment = deployment("current", "1.0.0", ref);
        when(flowStageResolver.resolveStartEnvironment()).thenReturn(dev);

        assertThat(classifier.classify(deployment)).isEqualTo(FlowType.AD_HOC);
    }

    private Deployment deployment(String externalId, String versionName, Environment environment) {
        ComponentVersion version = ComponentVersion.builder()
                .versionName(versionName)
                .versionControlUrl("https://git")
                .commitRef("ref")
                .committedAt(ZonedDateTime.now())
                .component(component)
                .deploymentUnit(DeploymentUnit.builder()
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("image:1.0.0")
                        .artifactRepositoryUrl("https://registry")
                        .build())
                .build();
        return Deployment.builder()
                .externalId(externalId)
                .startedAt(ZonedDateTime.now())
                .startedBy("tester")
                .environment(environment)
                .componentVersion(version)
                .sequence(DeploymentSequence.NEW)
                .deploymentTypes(Set.of(DeploymentType.CODE))
                .build();
    }
}
