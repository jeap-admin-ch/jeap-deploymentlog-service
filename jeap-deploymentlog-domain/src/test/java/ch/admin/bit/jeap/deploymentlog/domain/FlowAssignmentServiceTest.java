package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.AmbiguousOpenFlowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowAssignmentServiceTest {

    @Mock
    private FlowRepository flowRepository;
    @Mock
    private FlowTypeClassifier classifier;

    private FlowAssignmentService service;
    private Component component;
    private Environment dev;
    private Environment prod;

    @BeforeEach
    void setUp() {
        service = new FlowAssignmentService(flowRepository, classifier);
        component = new Component("service", new System("SYSTEM"));
        dev = new Environment("DEV");
        prod = new Environment("PROD");
    }

    @Test
    void ignoresDeploymentsWithoutCodeAndUndeployments() {
        Deployment config = deployment("config", "1.0.0", DeploymentSequence.NEW, DeploymentType.CONFIG);
        Deployment undeployment = deployment("undeploy", "1.0.0", DeploymentSequence.UNDEPLOYED, DeploymentType.CODE);

        assertThat(service.assign(config, prod)).isEmpty();
        assertThat(service.assign(undeployment, prod)).isEmpty();
        verify(flowRepository, never()).lockComponent(any());
    }

    @Test
    void createsOpenFlowWithInitialImmutableValues() {
        Deployment deployment = deployment("initial", "1.0.0", DeploymentSequence.NEW,
                DeploymentType.CODE, DeploymentType.CONFIG);
        when(classifier.classify(deployment)).thenReturn(FlowType.NEW);
        when(flowRepository.save(any(Flow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Flow flow = service.assign(deployment, prod).orElseThrow();

        assertThat(flow.getType()).isEqualTo(FlowType.NEW);
        assertThat(flow.getState()).isEqualTo(FlowState.OPEN);
        assertThat(flow.getBornAt()).isEqualTo(deployment.getStartedAt());
        assertThat(flow.getFinalDeploymentEnvironment()).isSameAs(prod);
        assertThat(flow.getDeployments()).containsExactly(deployment);
        assertThat(deployment.getFlow()).isSameAs(flow);
    }

    @Test
    void reusesBusinessVersionFlowWithoutReclassificationOrChangingTarget() {
        Deployment initial = deployment("initial", "1.0.0", DeploymentSequence.NEW, DeploymentType.CODE);
        Flow flow = Flow.start(FlowType.AD_HOC, initial, prod);
        Deployment retry = deployment("retry", "1.0.0", DeploymentSequence.REPEATED, DeploymentType.CODE);
        when(flowRepository.findOpenFlows(component.getId(), "1.0.0")).thenReturn(List.of(flow));

        Flow assigned = service.assign(retry, dev).orElseThrow();

        assertThat(assigned).isSameAs(flow);
        assertThat(flow.getType()).isEqualTo(FlowType.AD_HOC);
        assertThat(flow.getBornAt()).isEqualTo(initial.getStartedAt());
        assertThat(flow.getFinalDeploymentEnvironment()).isSameAs(prod);
        assertThat(flow.getDeployments()).containsExactly(initial, retry);
        verify(classifier, never()).classify(any());
    }

    @Test
    void processingAlreadyAssignedDeploymentIsIdempotent() {
        Deployment deployment = deployment("initial", "1.0.0", DeploymentSequence.NEW, DeploymentType.CODE);
        Flow flow = Flow.start(FlowType.NEW, deployment, prod);
        when(flowRepository.findByDeploymentId(deployment.getId())).thenReturn(Optional.of(flow));

        assertThat(service.assign(deployment, dev)).contains(flow);
        verify(flowRepository, never()).lockComponent(any());
        verify(classifier, never()).classify(any());
    }

    @Test
    void rejectsInconsistentMultipleOpenFlows() {
        Deployment first = deployment("first", "1.0.0", DeploymentSequence.NEW, DeploymentType.CODE);
        Deployment second = deployment("second", "1.0.0", DeploymentSequence.NEW, DeploymentType.CODE);
        Deployment candidate = deployment("candidate", "1.0.0", DeploymentSequence.NEW, DeploymentType.CODE);
        List<Flow> openFlows = List.of(Flow.start(FlowType.NEW, first, prod),
                Flow.start(FlowType.RETRY, second, prod));
        when(flowRepository.findOpenFlows(component.getId(), "1.0.0")).thenReturn(openFlows);

        assertThatThrownBy(() -> service.assign(candidate, prod))
                .isInstanceOf(AmbiguousOpenFlowException.class)
                .hasMessageContaining("Found 2 open flows");
    }

    private Deployment deployment(String externalId,
                                  String versionName,
                                  DeploymentSequence sequence,
                                  DeploymentType... deploymentTypes) {
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
                .environment(dev)
                .componentVersion(version)
                .sequence(sequence)
                .deploymentTypes(Set.of(deploymentTypes))
                .build();
    }
}
