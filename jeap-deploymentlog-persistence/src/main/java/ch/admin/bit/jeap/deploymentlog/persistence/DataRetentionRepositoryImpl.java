package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRefreshTask;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionResult;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class DataRetentionRepositoryImpl implements DataRetentionRepository {

    private static final Set<DeploymentState> RETAINABLE_TERMINAL_STATES =
            Set.of(DeploymentState.SUCCESS, DeploymentState.FAILURE, DeploymentState.CANCELLED);
    private static final Set<FlowState> TERMINAL_FLOW_STATES = Set.of(FlowState.CLOSED, FlowState.ABORTED);
    private static final ObjectMapper OBJECT_MAPPER = new JsonMapper();
    public static final String COMPONENT_VERSION_ID = "componentVersionId";
    public static final String FLOW_IDS = "flowIds";
    public static final String DEPLOYMENT_IDS = "deploymentIds";

    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<DataRetentionCandidate> findDeletionCandidates(ZonedDateTime cutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<DataRetentionCandidate> candidates = new ArrayList<>(findStandaloneCandidates(cutoff, limit));
        if (candidates.size() < limit) {
            List<UUID> flowIds = findTerminalFlowCandidates(cutoff, limit - candidates.size());
            if (!flowIds.isEmpty()) {
                candidates.addAll(entityManager.createQuery("""
                                select deployment.id, deployment.flow.id, system.name
                                from Deployment deployment
                                join deployment.componentVersion componentVersion
                                join componentVersion.component component
                                join component.system system
                                where deployment.flow.id in :flowIds
                                order by deployment.startedAt, deployment.id
                                """, Object[].class)
                        .setParameter(FLOW_IDS, flowIds)
                        .getResultList().stream()
                        .map(row -> new DataRetentionCandidate((UUID) row[0], (UUID) row[1], (String) row[2]))
                        .toList());
            }
        }
        return candidates;
    }

    private List<DataRetentionCandidate> findStandaloneCandidates(ZonedDateTime cutoff, int limit) {
        return entityManager.createQuery("""
                        select deployment.id, system.name
                        from Deployment deployment
                        join deployment.componentVersion componentVersion
                        join componentVersion.component component
                        join component.system system
                        where deployment.startedAt < :cutoff
                        and deployment.state in :terminalStates
                        and deployment.flow is null
                        and not exists (
                            select state.id from EnvironmentComponentVersionState state
                            where state.deployment = deployment
                        )
                        order by deployment.startedAt, deployment.id
                        """, Object[].class)
                .setParameter("cutoff", cutoff)
                .setParameter("terminalStates", RETAINABLE_TERMINAL_STATES)
                .setMaxResults(limit)
                .getResultList().stream()
                .map(row -> DataRetentionCandidate.standalone((UUID) row[0], (String) row[1]))
                .toList();
    }

    private List<UUID> findTerminalFlowCandidates(ZonedDateTime cutoff, int limit) {
        return entityManager.createQuery("""
                        select flow.id from Flow flow
                        where flow.state in :terminalFlowStates
                        and exists (
                            select deployment.id from Deployment deployment where deployment.flow = flow
                        )
                        and not exists (
                            select deployment.id from Deployment deployment
                            where deployment.flow = flow
                            and (deployment.startedAt >= :cutoff
                                or deployment.state not in :terminalDeploymentStates
                                or exists (
                                    select state.id from EnvironmentComponentVersionState state
                                    where state.deployment = deployment
                                ))
                        )
                        and not exists (
                            select referencingFlow.id from Flow referencingFlow
                            where referencingFlow.abortedBy = flow
                        )
                        order by flow.bornAt, flow.id
                        """, UUID.class)
                .setParameter("cutoff", cutoff)
                .setParameter("terminalFlowStates", TERMINAL_FLOW_STATES)
                .setParameter("terminalDeploymentStates", RETAINABLE_TERMINAL_STATES)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    @Transactional
    public DataRetentionResult deleteCandidates(Collection<UUID> deploymentIds, ZonedDateTime cutoff) {
        if (deploymentIds.isEmpty()) {
            return DataRetentionResult.empty();
        }

        Set<UUID> requestedIds = new HashSet<>(deploymentIds);
        List<Deployment> requestedDeployments = entityManager.createQuery("""
                        select distinct deployment from Deployment deployment
                        join fetch deployment.componentVersion componentVersion
                        join fetch componentVersion.component component
                        join fetch component.system system
                        join fetch deployment.environment environment
                        left join fetch deployment.changelog changelog
                        left join fetch deployment.flow flow
                        where deployment.id in :deploymentIds
                        """, Deployment.class)
                .setParameter(DEPLOYMENT_IDS, requestedIds)
                .getResultList();

        Set<UUID> currentStateDeploymentIds = new HashSet<>(entityManager.createQuery("""
                        select state.deployment.id from EnvironmentComponentVersionState state
                        where state.deployment.id in :deploymentIds
                        """, UUID.class)
                .setParameter(DEPLOYMENT_IDS, requestedIds)
                .getResultList());

        List<Deployment> standaloneDeployments = requestedDeployments.stream()
                .filter(deployment -> deployment.getFlow() == null)
                .filter(deployment -> isExpiredTerminalDeployment(deployment, cutoff, currentStateDeploymentIds))
                .toList();

        Set<UUID> requestedFlowIds = requestedDeployments.stream()
                .map(Deployment::getFlow)
                .filter(java.util.Objects::nonNull)
                .map(Flow::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> deletableFlowIds = findStillDeletableFlowIds(
                requestedFlowIds, requestedIds, cutoff, currentStateDeploymentIds);
        List<Deployment> flowDeployments = requestedDeployments.stream()
                .filter(deployment -> deployment.getFlow() != null)
                .filter(deployment -> deletableFlowIds.contains(deployment.getFlow().getId()))
                .toList();

        List<Deployment> deletableDeployments = new ArrayList<>(standaloneDeployments);
        deletableDeployments.addAll(flowDeployments);
        if (deletableDeployments.isEmpty()) {
            return DataRetentionResult.empty();
        }

        DataRetentionResult result = snapshotResult(deletableDeployments, deletableFlowIds.size());
        Set<UUID> deletableDeploymentIds = deletableDeployments.stream()
                .map(Deployment::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> componentVersionIds = deletableDeployments.stream()
                .map(Deployment::getComponentVersion)
                .map(ComponentVersion::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> changelogIds = deletableDeployments.stream()
                .map(Deployment::getChangelog)
                .filter(java.util.Objects::nonNull)
                .map(Changelog::getId)
                .collect(java.util.stream.Collectors.toSet());

        if (!deletableFlowIds.isEmpty()) {
            entityManager.createQuery("""
                            update Deployment deployment set deployment.flow = null
                            where deployment.id in :deploymentIds
                            """)
                    .setParameter(DEPLOYMENT_IDS, deletableDeploymentIds)
                    .executeUpdate();
            entityManager.clear();
        }

        deletableDeploymentIds.stream()
                .map(id -> entityManager.find(Deployment.class, id))
                .filter(java.util.Objects::nonNull)
                .forEach(entityManager::remove);
        entityManager.flush();

        deletableFlowIds.stream()
                .map(id -> entityManager.find(Flow.class, id))
                .filter(java.util.Objects::nonNull)
                .forEach(entityManager::remove);
        entityManager.flush();

        removeOrphanChangelogs(changelogIds);
        removeOrphanComponentVersions(componentVersionIds);
        persistRefreshTask(DataRetentionRefreshTask.from(result));
        entityManager.flush();
        return result;
    }

    private void persistRefreshTask(DataRetentionRefreshTask task) {
        entityManager.createNativeQuery("""
                        insert into data_retention_refresh_task (id, created_at, payload)
                        values (:id, :createdAt, :payload)
                        """)
                .setParameter("id", task.id())
                .setParameter("createdAt", ZonedDateTime.now())
                .setParameter("payload", serialize(task.result()))
                .executeUpdate();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataRetentionRefreshTask> findPendingRefreshTasks(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select cast(id as varchar), payload from data_retention_refresh_task
                        order by created_at, id
                        """)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream()
                .map(row -> new DataRetentionRefreshTask(
                        UUID.fromString((String) row[0]), deserialize((String) row[1])))
                .toList();
    }

    @Override
    @Transactional
    public void deletePendingRefreshTask(UUID refreshTaskId) {
        entityManager.createNativeQuery("delete from data_retention_refresh_task where id = :id")
                .setParameter("id", refreshTaskId)
                .executeUpdate();
    }

    private static String serialize(DataRetentionResult result) {
        return OBJECT_MAPPER.writeValueAsString(RefreshPayload.from(result));
    }

    private static DataRetentionResult deserialize(String payload) {
        return OBJECT_MAPPER.readValue(payload, RefreshPayload.class).toResult();
    }

    private record RefreshPayload(Set<SystemEnvironmentPayload> systemEnvironments,
                                  Set<UUID> componentIds,
                                  Set<UUID> environmentIds,
                                  Set<String> jiraIssueKeys,
                                  int deletedDeployments,
                                  int deletedFlows) {

        private static RefreshPayload from(DataRetentionResult result) {
            Set<SystemEnvironmentPayload> systemEnvironments = result.systemEnvironments().stream()
                    .map(SystemEnvironmentPayload::from)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new RefreshPayload(systemEnvironments, result.componentIds(), result.environmentIds(),
                    result.jiraIssueKeys(), result.deletedDeployments(), result.deletedFlows());
        }

        private DataRetentionResult toResult() {
            Set<SystemEnv> resultSystemEnvironments = systemEnvironments.stream()
                    .map(SystemEnvironmentPayload::toSystemEnv)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new DataRetentionResult(resultSystemEnvironments, componentIds, environmentIds, jiraIssueKeys,
                    deletedDeployments, deletedFlows, Set.of());
        }
    }

    private record SystemEnvironmentPayload(UUID systemId, String systemName, UUID environmentId) {

        private static SystemEnvironmentPayload from(SystemEnv systemEnv) {
            return new SystemEnvironmentPayload(
                    systemEnv.getSystemId(), systemEnv.getSystemName(), systemEnv.getEnvId());
        }

        private SystemEnv toSystemEnv() {
            return new SystemEnv(systemId, systemName, environmentId);
        }
    }

    private Set<UUID> findStillDeletableFlowIds(Set<UUID> flowIds,
                                                 Set<UUID> requestedDeploymentIds,
                                                 ZonedDateTime cutoff,
                                                 Set<UUID> currentStateDeploymentIds) {
        if (flowIds.isEmpty()) {
            return Set.of();
        }
        List<Flow> flows = entityManager.createQuery("""
                        select distinct flow from Flow flow
                        left join fetch flow.deployments
                        where flow.id in :flowIds
                        """, Flow.class)
                .setParameter(FLOW_IDS, flowIds)
                .getResultList();
        Set<UUID> referencedFlowIds = new HashSet<>(entityManager.createQuery("""
                        select distinct flow.abortedBy.id from Flow flow
                        where flow.abortedBy.id in :flowIds
                        """, UUID.class)
                .setParameter(FLOW_IDS, flowIds)
                .getResultList());

        return flows.stream()
                .filter(flow -> TERMINAL_FLOW_STATES.contains(flow.getState()))
                .filter(flow -> !referencedFlowIds.contains(flow.getId()))
                .filter(flow -> !flow.getDeployments().isEmpty())
                .filter(flow -> flow.getDeployments().stream().allMatch(deployment ->
                        requestedDeploymentIds.contains(deployment.getId())
                                && isExpiredTerminalDeployment(deployment, cutoff, currentStateDeploymentIds)))
                .map(Flow::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean isExpiredTerminalDeployment(Deployment deployment,
                                                ZonedDateTime cutoff,
                                                Set<UUID> currentStateDeploymentIds) {
        return deployment.getStartedAt().isBefore(cutoff)
                && RETAINABLE_TERMINAL_STATES.contains(deployment.getState())
                && !currentStateDeploymentIds.contains(deployment.getId())
                && (deployment.getFlow() == null || deployment.getFlow().getState() != FlowState.OPEN);
    }

    private DataRetentionResult snapshotResult(List<Deployment> deployments, int deletedFlows) {
        Set<SystemEnv> systemEnvironments = new LinkedHashSet<>();
        Set<UUID> componentIds = new LinkedHashSet<>();
        Set<UUID> environmentIds = new LinkedHashSet<>();
        Set<String> jiraIssueKeys = new LinkedHashSet<>();
        for (Deployment deployment : deployments) {
            var component = deployment.getComponentVersion().getComponent();
            var system = component.getSystem();
            var environment = deployment.getEnvironment();
            systemEnvironments.add(new SystemEnv(system.getId(), system.getName(), environment.getId()));
            componentIds.add(component.getId());
            environmentIds.add(environment.getId());
            if (deployment.getChangelog() != null) {
                jiraIssueKeys.addAll(deployment.getChangelog().getJiraIssueKeys());
            }
        }
        return new DataRetentionResult(Set.copyOf(systemEnvironments), Set.copyOf(componentIds),
                Set.copyOf(environmentIds), Set.copyOf(jiraIssueKeys), deployments.size(), deletedFlows,
                deployments.stream().map(Deployment::getId).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private void removeOrphanChangelogs(Set<UUID> changelogIds) {
        for (UUID changelogId : changelogIds) {
            Long references = entityManager.createQuery("""
                            select count(deployment) from Deployment deployment
                            where deployment.changelog.id = :changelogId
                            """, Long.class)
                    .setParameter("changelogId", changelogId)
                    .getSingleResult();
            if (references == 0) {
                Changelog changelog = entityManager.find(Changelog.class, changelogId);
                if (changelog != null) {
                    entityManager.remove(changelog);
                }
            }
        }
    }

    private void removeOrphanComponentVersions(Set<UUID> componentVersionIds) {
        for (UUID componentVersionId : componentVersionIds) {
            Long references = entityManager.createQuery("""
                            select count(deployment) from Deployment deployment
                            where deployment.componentVersion.id = :componentVersionId
                            """, Long.class)
                    .setParameter(COMPONENT_VERSION_ID, componentVersionId)
                    .getSingleResult();
            references += entityManager.createQuery("""
                            select count(flow) from Flow flow
                            where flow.componentVersion.id = :componentVersionId
                            """, Long.class)
                    .setParameter(COMPONENT_VERSION_ID, componentVersionId)
                    .getSingleResult();
            references += entityManager.createQuery("""
                            select count(state) from EnvironmentComponentVersionState state
                            where state.componentVersion.id = :componentVersionId
                            """, Long.class)
                    .setParameter(COMPONENT_VERSION_ID, componentVersionId)
                    .getSingleResult();
            if (references == 0) {
                ComponentVersion componentVersion = entityManager.find(ComponentVersion.class, componentVersionId);
                if (componentVersion != null) {
                    entityManager.remove(componentVersion);
                }
            }
        }
    }
}
