package ch.admin.bit.jeap.deploymentlog.domain;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowLifecycleServiceTest {

    @Mock
    private FlowRepository flowRepository;

    private FlowLifecycleService service;
    private Component component;
    private Environment dev;
    private Environment prod;

    @BeforeEach
    void setUp() {
        FlowStageProperties properties = new FlowStageProperties();
        service = new FlowLifecycleService(flowRepository, properties);
        component = new Component("service", new System("SYSTEM"));
        dev = new Environment("DEV");
        prod = new Environment("PROD");
    }

    @Test
    void ignoresSuccessfulCodeDeploymentWhenFlowProcessingIsDisabled() {
        FlowStageProperties properties = new FlowStageProperties();
        properties.setEnabled(false);
        service = new FlowLifecycleService(flowRepository, properties);
        Deployment deployment = deployment("disabled", "1.0.0", ZonedDateTime.now(), prod, DeploymentType.CODE);
        deployment.success(ZonedDateTime.now(), "done");

        service.process(deployment);

        verify(flowRepository, never()).lockComponent(any());
        verify(flowRepository, never()).findByDeploymentIdAndLockComponent(any());
    }

    @Test
    void successfulProductiveTargetClosesOwnFlowAndAbortsOlderOpenFlows() {
        ZonedDateTime winningCommit = ZonedDateTime.parse("2026-08-20T12:00:00+02:00");
        Deployment winningDeployment = deployment("winner", "2.0.0", winningCommit, prod, DeploymentType.CODE);
        Flow winningFlow = Flow.start(FlowType.NEW, winningDeployment, prod);
        winningDeployment.success(ZonedDateTime.now(), "done");
        Flow older = Flow.start(FlowType.AD_HOC,
                deployment("older", "z-version-name-is-irrelevant", winningCommit.minusDays(1), dev, DeploymentType.CODE), prod);
        when(flowRepository.findByDeploymentIdAndLockComponent(winningDeployment.getId()))
                .thenReturn(Optional.of(winningFlow));
        when(flowRepository.findOlderOpenFlows(component.getId(), winningCommit, winningFlow.getId()))
                .thenReturn(List.of(older));

        service.process(winningDeployment);

        assertThat(winningFlow.getState()).isEqualTo(FlowState.CLOSED);
        assertThat(older.getState()).isEqualTo(FlowState.ABORTED);
        assertThat(older.getAbortedBy()).isSameAs(winningFlow);
        verify(flowRepository).findByDeploymentIdAndLockComponent(winningDeployment.getId());
    }

    @Test
    void successfulNonProductiveTargetOnlyClosesOwnFlow() {
        Deployment deployment = deployment("winner", "1.0.0", ZonedDateTime.now(), dev, DeploymentType.CODE);
        Flow flow = Flow.start(FlowType.RETRY, deployment, dev);
        deployment.success(ZonedDateTime.now(), "done");
        when(flowRepository.findByDeploymentIdAndLockComponent(deployment.getId())).thenReturn(Optional.of(flow));

        service.process(deployment);

        assertThat(flow.getState()).isEqualTo(FlowState.CLOSED);
        verify(flowRepository, never()).findOlderOpenFlows(any(), any(), any());
    }

    @Test
    void failedOrNonCodeDeploymentDoesNotAcquireLockOrChangeFlows() {
        Deployment failed = deployment("failed", "1.0.0", ZonedDateTime.now(), prod, DeploymentType.CODE);
        failed.failed(ZonedDateTime.now(), "failed");
        Deployment config = deployment("config", "1.0.0", ZonedDateTime.now(), prod, DeploymentType.CONFIG);
        config.success(ZonedDateTime.now(), "done");

        service.process(failed);
        service.process(config);

        verify(flowRepository, never()).lockComponent(any());
        verify(flowRepository, never()).findByDeploymentIdAndLockComponent(any());
        verify(flowRepository, never()).findByDeploymentId(any());
    }

    @Test
    void successfulCodeDeploymentWithoutFlowDoesNotAcquireComponentLock() {
        Deployment deployment = deployment("legacy", "1.0.0", ZonedDateTime.now(), prod, DeploymentType.CODE);
        deployment.success(ZonedDateTime.now(), "done");
        when(flowRepository.findByDeploymentIdAndLockComponent(deployment.getId())).thenReturn(Optional.empty());

        service.process(deployment);

        verify(flowRepository, never()).lockComponent(any());
        verify(flowRepository, never()).findByDeploymentId(any());
    }

    @Test
    void repeatedProcessingKeepsTerminalStatesAndAbortedByStable() {
        ZonedDateTime committedAt = ZonedDateTime.now();
        Deployment deployment = deployment("winner", "2.0.0", committedAt, prod, DeploymentType.CODE);
        Flow winner = Flow.start(FlowType.NEW, deployment, prod);
        deployment.success(ZonedDateTime.now(), "done");
        Flow older = Flow.start(FlowType.NEW,
                deployment("older", "1.0.0", committedAt.minusDays(1), dev, DeploymentType.CODE), prod);
        when(flowRepository.findByDeploymentIdAndLockComponent(deployment.getId()))
                .thenReturn(Optional.of(winner));
        when(flowRepository.findOlderOpenFlows(component.getId(), committedAt, winner.getId()))
                .thenReturn(List.of(older))
                .thenReturn(List.of());

        service.process(deployment);
        service.process(deployment);

        assertThat(winner.getState()).isEqualTo(FlowState.CLOSED);
        assertThat(older.getState()).isEqualTo(FlowState.ABORTED);
        assertThat(older.getAbortedBy()).isSameAs(winner);
    }

    private Deployment deployment(String externalId,
                                  String versionName,
                                  ZonedDateTime committedAt,
                                  Environment environment,
                                  DeploymentType deploymentType) {
        ComponentVersion version = ComponentVersion.builder()
                .versionName(versionName)
                .versionControlUrl("https://git")
                .commitRef(externalId)
                .committedAt(committedAt)
                .component(component)
                .deploymentUnit(DeploymentUnit.builder()
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("image:" + versionName)
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
                .deploymentTypes(Set.of(deploymentType))
                .build();
    }
}
