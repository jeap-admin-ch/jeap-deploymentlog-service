package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRefreshTask;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionResult;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnit;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnitType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentComponentVersionState;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentComponentVersionStateRepository;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class DataRetentionRepositoryImplTest {

    @Autowired
    private DataRetentionRepository dataRetentionRepository;
    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EnvironmentComponentVersionStateRepository stateRepository;
    @Autowired
    private EntityManager entityManager;

    private Environment environment;
    private Component component;
    private ZonedDateTime cutoff;

    @BeforeEach
    void setUp() {
        environment = environmentRepository.save(new Environment("DEV"));
        System system = systemRepository.save(new System("test-system"));
        component = componentRepository.save(new Component("test-component", system));
        cutoff = ZonedDateTime.now().minusDays(30);
    }

    @Test
    void selectsExpiredTerminalDeploymentsWithoutCurrentStageState() {
        Deployment oldSuccess = deployment(cutoff.minusDays(1), "JEAP-1");
        oldSuccess.success(cutoff.minusDays(1).plusMinutes(1), null);
        Deployment oldFailure = deployment(cutoff.minusDays(2), "JEAP-2");
        oldFailure.failed(cutoff.minusDays(2).plusMinutes(1), null);
        Deployment oldStarted = deployment(cutoff.minusDays(3), "JEAP-3");
        Deployment oldCancelled = deployment(cutoff.minusDays(4), "JEAP-4");
        oldCancelled.cancelled(cutoff.minusDays(4).plusMinutes(1), null);
        Deployment recentSuccess = deployment(cutoff.plusDays(1), "JEAP-5");
        recentSuccess.success(cutoff.plusDays(1).plusMinutes(1), null);
        save(oldSuccess, oldFailure, oldStarted, oldCancelled, recentSuccess);
        stateRepository.save(EnvironmentComponentVersionState.fromDeployment(oldSuccess));

        assertThat(candidateIds())
                .containsExactlyInAnyOrder(oldFailure.getId(), oldCancelled.getId());
    }

    @Test
    void protectsOpenFlowAndRequiresEveryDeploymentOfTerminalFlowToExpire() {
        Deployment openDeployment = terminalDeployment(cutoff.minusDays(5), "JEAP-1");
        save(openDeployment);
        flowRepository.save(Flow.start(FlowType.NEW, openDeployment, environment));

        Deployment oldInClosedFlow = terminalDeployment(cutoff.minusDays(4), "JEAP-2", "2.0.0");
        Deployment recentInClosedFlow = terminalDeployment(cutoff.plusDays(1), "JEAP-2", "2.0.0");
        save(oldInClosedFlow, recentInClosedFlow);
        Flow mixedFlow = Flow.start(FlowType.NEW, oldInClosedFlow, environment);
        mixedFlow.add(recentInClosedFlow);
        mixedFlow.closeIfTargetReached(recentInClosedFlow);
        flowRepository.save(mixedFlow);
        entityManager.flush();

        assertThat(candidateIds()).isEmpty();
    }

    @Test
    void deletesCompleteTerminalFlowAndOrphanDetailsButKeepsStructure() {
        Deployment first = terminalDeployment(cutoff.minusDays(5), "jeap-1", "1.0.0");
        first.cancelled(cutoff.minusDays(5).plusMinutes(2), null);
        Deployment second = terminalDeployment(cutoff.minusDays(4), "JEAP-1", "1.0.0");
        save(first, second);
        Flow flow = Flow.start(FlowType.NEW, first, environment);
        flow.add(second);
        flow.closeIfTargetReached(second);
        flowRepository.save(flow);
        entityManager.flush();

        List<DataRetentionCandidate> candidates = dataRetentionRepository.findDeletionCandidates(cutoff, 500);
        assertThat(candidates).extracting(DataRetentionCandidate::deploymentId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(candidates).extracting(DataRetentionCandidate::retentionUnitId)
                .containsOnly(flow.getId());
        assertThat(candidates).extracting(DataRetentionCandidate::systemName)
                .containsOnly("test-system");

        DataRetentionResult result = dataRetentionRepository.deleteCandidates(
                candidates.stream().map(DataRetentionCandidate::deploymentId).toList(), cutoff);
        entityManager.flush();
        entityManager.clear();

        assertThat(result.deletedDeployments()).isEqualTo(2);
        assertThat(result.deletedFlows()).isEqualTo(1);
        assertThat(result.deletedDeploymentIds()).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(result.componentIds()).containsExactly(component.getId());
        assertThat(result.jiraIssueKeys()).containsExactlyInAnyOrder("jeap-1", "JEAP-1");
        List<DataRetentionRefreshTask> refreshTasks = dataRetentionRepository.findPendingRefreshTasks(10);
        assertThat(refreshTasks).hasSize(1);
        assertThat(refreshTasks.getFirst().result().systemEnvironments())
                .containsExactlyElementsOf(result.systemEnvironments());
        assertThat(refreshTasks.getFirst().result().componentIds()).containsExactly(component.getId());
        assertThat(refreshTasks.getFirst().result().jiraIssueKeys())
                .containsExactlyInAnyOrder("jeap-1", "JEAP-1");
        assertThat(count("Deployment")).isZero();
        assertThat(count("Flow")).isZero();
        assertThat(count("ComponentVersion")).isZero();
        assertThat(count("Changelog")).isZero();
        assertThat(count("Component")).isEqualTo(1);
        assertThat(count("System")).isEqualTo(1);
        assertThat(count("Environment")).isEqualTo(1);

        dataRetentionRepository.deletePendingRefreshTask(refreshTasks.getFirst().id());
        entityManager.flush();
        assertThat(dataRetentionRepository.findPendingRefreshTasks(10)).isEmpty();
    }

    @Test
    void rechecksCompleteFlowWhenOnlyPartOfCandidateSetIsApproved() {
        Deployment first = terminalDeployment(cutoff.minusDays(5), "JEAP-1", "1.0.0");
        Deployment second = terminalDeployment(cutoff.minusDays(4), "JEAP-1", "1.0.0");
        save(first, second);
        Flow flow = Flow.start(FlowType.NEW, first, environment);
        flow.add(second);
        flow.closeIfTargetReached(second);
        flowRepository.save(flow);
        entityManager.flush();

        DataRetentionResult result = dataRetentionRepository.deleteCandidates(Set.of(first.getId()), cutoff);

        assertThat(result.isEmpty()).isTrue();
        assertThat(count("Deployment")).isEqualTo(2);
        assertThat(count("Flow")).isEqualTo(1);
    }

    private Set<UUID> candidateIds() {
        return dataRetentionRepository.findDeletionCandidates(cutoff, 500).stream()
                .map(DataRetentionCandidate::deploymentId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Deployment terminalDeployment(ZonedDateTime startedAt, String issueKey) {
        return terminalDeployment(startedAt, issueKey, UUID.randomUUID().toString());
    }

    private Deployment terminalDeployment(ZonedDateTime startedAt, String issueKey, String versionName) {
        Deployment deployment = deployment(startedAt, issueKey, versionName);
        deployment.success(startedAt.plusMinutes(1), null);
        return deployment;
    }

    private Deployment deployment(ZonedDateTime startedAt, String issueKey) {
        return deployment(startedAt, issueKey, UUID.randomUUID().toString());
    }

    private Deployment deployment(ZonedDateTime startedAt, String issueKey, String versionName) {
        ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName(versionName)
                .taggedAt(startedAt)
                .committedAt(startedAt)
                .versionControlUrl("https://git.example/commit")
                .commitRef(UUID.randomUUID().toString())
                .publishedVersion(true)
                .component(component)
                .deploymentUnit(DeploymentUnit.builder()
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("example/image")
                        .artifactRepositoryUrl("https://registry.example")
                        .build())
                .build();
        return Deployment.builder()
                .externalId(UUID.randomUUID().toString())
                .startedAt(startedAt)
                .startedBy("tester")
                .environment(environment)
                .componentVersion(componentVersion)
                .changelog(Changelog.builder().jiraIssueKeys(Set.of(issueKey)).build())
                .sequence(DeploymentSequence.NEW)
                .deploymentTypes(Set.of(DeploymentType.CODE))
                .build();
    }

    private void save(Deployment... deployments) {
        List.of(deployments).forEach(entityManager::persist);
        entityManager.flush();
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(entity) from " + entityName + " entity", Long.class)
                .getSingleResult();
    }
}
