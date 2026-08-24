package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowLifecycleService;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowStageProperties;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Import({FlowLifecycleService.class, FlowStageProperties.class})
class FlowRepositoryImplTest {

    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private FlowLifecycleService flowLifecycleService;

    private Environment dev;
    private Environment prod;
    private Component component;

    @BeforeEach
    void setUp() {
        dev = environmentRepository.save(new Environment("DEV"));
        prod = environmentRepository.save(new Environment("PROD"));
        System system = systemRepository.save(new System("SYSTEM"));
        component = componentRepository.save(new Component("service", system));
    }

    @Test
    void persistsAndFindsFlowByBusinessVersionAndDeployment() {
        Deployment deployment = deploymentRepository.save(deployment("1.0.0"));
        Flow flow = flowRepository.save(Flow.start(FlowType.NEW, deployment, prod));
        Deployment deploymentWithoutFlow = deploymentRepository.save(deployment("2.0.0"));
        entityManager.flush();
        entityManager.clear();

        List<Flow> openFlows = flowRepository.findOpenFlows(component.getId(), "1.0.0");

        assertThat(openFlows).extracting(Flow::getId).containsExactly(flow.getId());
        assertThat(flowRepository.findByDeploymentIdAndLockComponent(deployment.getId()))
                .map(Flow::getId).contains(flow.getId());
        assertThat(flowRepository.findByDeploymentIdAndLockComponent(deploymentWithoutFlow.getId())).isEmpty();
        assertThat(flowRepository.findByDeploymentId(deployment.getId())).map(Flow::getId).contains(flow.getId());
        assertThat(openFlows.getFirst().getDeployments()).extracting(Deployment::getId)
                .containsExactly(deployment.getId());
    }

    @Test
    void componentCanBeLockedForCrossInstanceSerialization() {
        entityManager.flush();
        UUID componentId = component.getId();

        assertThatCode(() -> flowRepository.lockComponent(componentId))
                .doesNotThrowAnyException();
    }

    @Test
    void findsOnlyStrictlyOlderOpenFlowsOfSameComponent() {
        ZonedDateTime winningCommit = ZonedDateTime.parse("2026-08-20T12:00:00+02:00");
        Flow older = persistFlow("9.0.0", winningCommit.minusDays(1), component, dev);
        persistFlow("1.0.0", winningCommit, component, dev);
        persistFlow("0.1.0", winningCommit.plusDays(1), component, dev);
        Component otherComponent = componentRepository.save(new Component("other-service", component.getSystem()));
        persistFlow("old-other-component", winningCommit.minusDays(2), otherComponent, dev);
        System otherSystem = systemRepository.save(new System("OTHER-SYSTEM"));
        Component sameNamedComponent = componentRepository.save(new Component(component.getName(), otherSystem));
        persistFlow("old-same-name-other-system", winningCommit.minusDays(2), sameNamedComponent, dev);
        Flow terminalOlder = persistFlow("terminal", winningCommit.minusDays(3), component, dev);
        Flow abortingFlow = persistFlow("aborting", winningCommit, component, prod);
        terminalOlder.abortBy(abortingFlow);
        entityManager.flush();
        entityManager.clear();

        List<Flow> candidates = flowRepository.findOlderOpenFlows(
                component.getId(), winningCommit, abortingFlow.getId());

        assertThat(candidates).extracting(Flow::getId).containsExactly(older.getId());
    }

    @Test
    void lifecycleClosesWinningFlowAndPersistsAbortReference() {
        ZonedDateTime winningCommit = ZonedDateTime.parse("2026-08-20T12:00:00+02:00");
        Flow older = persistFlow("older", winningCommit.minusDays(1), component, dev);
        UUID olderDeploymentId = older.getDeployments().getFirst().getId();
        Deployment winningDeployment = deploymentRepository.save(
                deployment("winner", winningCommit, component, prod));
        flowRepository.save(Flow.start(FlowType.NEW, winningDeployment, prod));
        entityManager.flush();
        winningDeployment.success(ZonedDateTime.now(), "done");

        flowLifecycleService.process(winningDeployment);
        entityManager.flush();
        entityManager.clear();

        Flow persistedWinner = flowRepository.findByDeploymentId(winningDeployment.getId()).orElseThrow();
        Flow persistedOlder = flowRepository.findByDeploymentId(olderDeploymentId).orElseThrow();
        assertThat(persistedWinner.getState()).isEqualTo(FlowState.CLOSED);
        assertThat(persistedOlder.getState()).isEqualTo(FlowState.ABORTED);
        assertThat(persistedOlder.getAbortedBy().getId()).isEqualTo(persistedWinner.getId());
    }

    private Flow persistFlow(String versionName,
                             ZonedDateTime committedAt,
                             Component flowComponent,
                             Environment environment) {
        Deployment deployment = deploymentRepository.save(
                deployment(versionName, committedAt, flowComponent, environment));
        return flowRepository.save(Flow.start(FlowType.NEW, deployment, prod));
    }

    private Deployment deployment(String versionName) {
        return deployment(versionName, ZonedDateTime.now(), component, dev);
    }

    private Deployment deployment(String versionName,
                                  ZonedDateTime committedAt,
                                  Component deploymentComponent,
                                  Environment environment) {
        Deployment deployment = TestDataFactory.createDeployment(environment, deploymentComponent,
                ZonedDateTime.now(), versionName, committedAt, TestDataFactory.createDeploymentTarget());
        deployment.getDeploymentTypes().add(DeploymentType.CODE);
        return deployment;
    }
}
