package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Changelog;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionRefreshTask;
import ch.admin.bit.jeap.deploymentlog.domain.DataRetentionResult;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZonedDateTime;
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
    private static final ObjectMapper OBJECT_MAPPER = new JsonMapper();
    public static final String COMPONENT_VERSION_ID = "componentVersionId";
    public static final String DEPLOYMENT_IDS = "deploymentIds";

    private final EntityManager entityManager;
    private final DeploymentRepository deploymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DataRetentionCandidate> findDeletionCandidates(ZonedDateTime cutoff, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return entityManager.createQuery("""
                        select deployment.id, system.name
                        from Deployment deployment
                        join deployment.componentVersion componentVersion
                        join componentVersion.component component
                        join component.system system
                        where deployment.startedAt < :cutoff
                        and deployment.state in :terminalStates
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
                .map(row -> new DataRetentionCandidate((UUID) row[0], (String) row[1]))
                .toList();
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

        List<Deployment> deletableDeployments = requestedDeployments.stream()
                .filter(deployment -> isExpiredTerminalDeployment(deployment, cutoff, currentStateDeploymentIds))
                .toList();

        if (deletableDeployments.isEmpty()) {
            return DataRetentionResult.empty();
        }

        // Persist version identities before retention removes the source rows, including after a missed listener.
        deploymentRepository.reconcileTerminalDeploymentMetrics();
        DataRetentionResult result = snapshotResult(deletableDeployments);
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

        deletableDeploymentIds.stream()
                .map(id -> entityManager.find(Deployment.class, id))
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RefreshPayload(Set<SystemEnvironmentPayload> systemEnvironments,
                                  Set<UUID> componentIds,
                                  Set<UUID> environmentIds,
                                  Set<String> jiraIssueKeys,
                                  int deletedDeployments) {

        private static RefreshPayload from(DataRetentionResult result) {
            Set<SystemEnvironmentPayload> systemEnvironments = result.systemEnvironments().stream()
                    .map(SystemEnvironmentPayload::from)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new RefreshPayload(systemEnvironments, result.componentIds(), result.environmentIds(),
                    result.jiraIssueKeys(), result.deletedDeployments());
        }

        private DataRetentionResult toResult() {
            Set<SystemEnv> resultSystemEnvironments = systemEnvironments.stream()
                    .map(SystemEnvironmentPayload::toSystemEnv)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new DataRetentionResult(resultSystemEnvironments, componentIds, environmentIds, jiraIssueKeys,
                    deletedDeployments, Set.of());
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

    private boolean isExpiredTerminalDeployment(Deployment deployment,
                                                ZonedDateTime cutoff,
                                                Set<UUID> currentStateDeploymentIds) {
        return deployment.getStartedAt().isBefore(cutoff)
                && RETAINABLE_TERMINAL_STATES.contains(deployment.getState())
                && !currentStateDeploymentIds.contains(deployment.getId());
    }

    private DataRetentionResult snapshotResult(List<Deployment> deployments) {
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
                Set.copyOf(environmentIds), Set.copyOf(jiraIssueKeys), deployments.size(),
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
