package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricValue;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class DeploymentRepositoryImpl implements DeploymentRepository {

    private final JpaDeploymentRepository jpaDeploymentRepository;
    private final EntityManager entityManager;

    @Override
    public boolean isPageGenerationRepairRequired(UUID deploymentId) {
        return jpaDeploymentRepository.isPageGenerationRepairRequired(deploymentId);
    }

    @Override
    public Optional<UUID> getPageGenerationRequestId(UUID deploymentId) {
        return jpaDeploymentRepository.getPageGenerationRequestId(deploymentId);
    }

    @Override
    public void completePageGenerationRequest(UUID deploymentId, UUID requestId) {
        jpaDeploymentRepository.completePageGenerationRequest(deploymentId, requestId);
    }

    @Override
    public void resumePageGeneration(UUID deploymentId) {
        jpaDeploymentRepository.resumePageGeneration(deploymentId, UUID.randomUUID());
    }

    @Override
    public void suppressPageGeneration(UUID deploymentId, ZonedDateTime pageStateTimestamp) {
        jpaDeploymentRepository.suppressPageGeneration(deploymentId, pageStateTimestamp);
    }

    @Override
    public int releaseLegacyPageGeneration(int limit, ZonedDateTime from, ZonedDateTime to) {
        List<UUID> deploymentIds = jpaDeploymentRepository.findLegacyPageGenerationIds(
                from, to, PageRequest.of(0, limit));
        if (!deploymentIds.isEmpty()) {
            jpaDeploymentRepository.releaseLegacyPageGeneration(deploymentIds);
        }
        return deploymentIds.size();
    }

    @Override
    public Deployment save(Deployment deployment) {
        return jpaDeploymentRepository.save(deployment);
    }

    @Override
    public Deployment getById(UUID id) {
        return jpaDeploymentRepository.findById(id).orElseThrow();
    }

    @Override
    public Optional<Deployment> findById(UUID id) {
        return jpaDeploymentRepository.findById(id);
    }

    @Override
    public Optional<Deployment> findByExternalId(String externalId) {
        return jpaDeploymentRepository.findByExternalId(externalId);
    }

    @Override
    public Optional<Deployment> findByExternalIdForUpdate(String externalId) {
        return jpaDeploymentRepository.findByExternalIdForUpdate(externalId);
    }

    @Override
    public List<Deployment> findAllDeploymentForSystemAndEnv(System system, Environment environment) {
        return jpaDeploymentRepository.findAllDeploymentForSystemAndEnv(environment.getId(), system.getId());
    }

    @Override
    public List<Deployment> findAllDeploymentsForSystemStartedBetween(System system, ZonedDateTime from, ZonedDateTime to) {
        return jpaDeploymentRepository.findAllDeploymentsForSystemStartedBetween(system.getId(), from, to);
    }

    @Override
    public List<Integer> findAllDeploymentsYearsForSystemAndEnv(System system, Environment environment) {
        return jpaDeploymentRepository.findAllDeploymentForSystemAndEnv(environment.getId(), system.getId())
                .stream()
                .map(deployment -> deployment.getStartedAt().getYear())
                .distinct()
                .toList();
    }

    @Override
    public List<Deployment> findDeploymentForSystemAndEnvLimited(System system, Environment environment, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Deployment> deploymentPage = jpaDeploymentRepository.findDeploymentForSystemAndEnvLimited(
                environment.getId(),
                system.getId(),
                pageable);
        return deploymentPage.stream().toList();
    }

    @Override
    public List<Deployment> findDeploymentForEnvLimited(Environment environment, ZonedDateTime minStartedAt, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Deployment> deploymentPage = jpaDeploymentRepository.findDeploymentForEnvLimited(
                environment.getId(),
                minStartedAt,
                pageable);
        return deploymentPage.stream().toList();
    }

    @Override
    public List<UUID> getDeploymentIdsWithMissingOrOutdatedGeneratedPages(int limit, ZonedDateTime from, ZonedDateTime to) {
        Pageable pageable = PageRequest.of(0, limit);
        return jpaDeploymentRepository.getDeploymentIdsMissingOrOutdatedGeneratedPages(from, to, pageable);
    }

    @Override
    public List<UUID> getDeploymentIdsWithMissingOrOutdatedGeneratedPages(int limit, ZonedDateTime to) {
        Pageable pageable = PageRequest.of(0, limit);
        return jpaDeploymentRepository.getAllDeploymentIdsMissingOrOutdatedGeneratedPages(to, pageable);
    }

    @Override
    public void markPageGenerationAttempted(List<UUID> deploymentIds, ZonedDateTime attemptedAt) {
        jpaDeploymentRepository.markPageGenerationAttempted(deploymentIds, attemptedAt);
    }

    @Override
    public long countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime from) {
        return jpaDeploymentRepository.countDeploymentsWithMissingOrOutdatedGeneratedPages(from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentMetricIdentity> findDeploymentMetricIdentities() {
        return jpaDeploymentRepository.findMetricIdentities();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentMetricIdentity> findStartedDeploymentMetricIdentities() {
        return jpaDeploymentRepository.findMetricIdentitiesByState(DeploymentState.STARTED);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTerminalDeploymentMetric(DeploymentTerminalMetricEvent event) {
        List<String> requestedTypes = event.deploymentTypes().stream()
                .sorted()
                .map(Enum::name)
                .toList();
        if (requestedTypes.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> existingTypes = Set.copyOf(entityManager.createNativeQuery("""
                        select deployment_type
                        from deployment_metric_event
                        where deployment_id = :deploymentId
                          and deployment_type in (:deploymentTypes)
                        """)
                .setParameter("deploymentId", event.deploymentId())
                .setParameter("deploymentTypes", requestedTypes)
                .getResultList());
        List<String> deploymentTypes = requestedTypes.stream()
                .filter(type -> !existingTypes.contains(type))
                .toList();
        if (deploymentTypes.isEmpty()) {
            return;
        }

        String values = IntStream.range(0, deploymentTypes.size())
                .mapToObj(index -> "(:deploymentId, :deploymentType" + index + ", :system, :component, " +
                        ":environment, :state, :endedAt)")
                .collect(Collectors.joining(", "));
        var query = entityManager.createNativeQuery("""
                        insert into deployment_metric_event
                            (deployment_id, deployment_type, system_name, component_name,
                             environment_name, deployment_state, ended_at)
                        values %s
                        """.formatted(values))
                .setParameter("deploymentId", event.deploymentId())
                .setParameter("system", event.system())
                .setParameter("component", event.component())
                .setParameter("environment", event.environment())
                .setParameter("state", event.state().name())
                .setParameter("endedAt", event.endedAt());
        IntStream.range(0, deploymentTypes.size()).forEach(index ->
                query.setParameter("deploymentType" + index, deploymentTypes.get(index)));
        query.executeUpdate();
    }

    @Override
    @Transactional
    public void reconcileTerminalDeploymentMetrics() {
        entityManager.createNativeQuery("""
                        insert into deployment_metric_event
                            (deployment_id, deployment_type, system_name, component_name,
                             environment_name, deployment_state, ended_at)
                        select deployment.id,
                               deployment_type.type,
                               system.name,
                               component.name,
                               environment.name,
                               deployment.state,
                               deployment.ended_at
                        from deployment
                        join deployment_types deployment_type on deployment_type.deployment_id = deployment.id
                        join component_version on component_version.id = deployment.component_version_id
                        join component on component.id = component_version.component_id
                        join system on system.id = component.system_id
                        join environment on environment.id = deployment.environment_id
                        where deployment.state in ('SUCCESS', 'FAILURE', 'CANCELLED')
                          and not exists (
                              select 1
                              from deployment_metric_event metric_event
                              where metric_event.deployment_id = deployment.id
                                and metric_event.deployment_type = deployment_type.type
                          )
                        """)
                .executeUpdate();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentMetricValue> findDeploymentMetricValues() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select system_name, component_name, environment_name, deployment_type, deployment_state, count(*)
                        from deployment_metric_event
                        where deployment_state in ('SUCCESS', 'FAILURE', 'CANCELLED')
                        group by system_name, component_name, environment_name, deployment_type, deployment_state
                        """)
                .getResultList();
        return rows.stream()
                .map(row -> new DeploymentMetricValue(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        DeploymentType.valueOf((String) row[3]),
                        DeploymentState.valueOf((String) row[4]),
                        ((Number) row[5]).longValue()))
                .toList();
    }

    @Override
    public Optional<Deployment> getLastDeploymentForComponent(ch.admin.bit.jeap.deploymentlog.domain.Component component,
                                                              Environment env) {
        List<Deployment> results = jpaDeploymentRepository.getLastDeploymentsForComponent(component, env, PageRequest.of(0, 1));
        return results.stream().findFirst();
    }

    @Override
    public Optional<Deployment> getLastSuccessfulDeploymentForComponent(ch.admin.bit.jeap.deploymentlog.domain.Component component,
                                                                        Environment env) {
        List<Deployment> results = jpaDeploymentRepository.getLastSuccessfulDeploymentsForComponent(component, env, PageRequest.of(0, 1));
        return results.stream().findFirst();
    }

    @Override
    public Optional<Deployment> getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(Component component, Environment env, String version) {
        List<Deployment> results = jpaDeploymentRepository.getSuccessfulCodeDeploymentsForComponentDifferentToVersion(
                component, env, version, PageRequest.of(0, 1));
        return results.stream().findFirst();
    }

    @Override
    public String getSystemNameForDeployment(UUID deploymentId) {
        return jpaDeploymentRepository.getSystemNameForDeployment(deploymentId);
    }

    @Override
    public String getComponentNameForDeployment(UUID deploymentId) {
        return jpaDeploymentRepository.getComponentNameForDeployment(deploymentId);
    }

    @Override
    public Optional<Deployment> getLastDeploymentForBusinessVersion(Component component,
                                                                    Environment environment,
                                                                    String versionName,
                                                                    UUID excludedDeploymentId) {
        return jpaDeploymentRepository.findLastDeploymentForBusinessVersion(component, environment, versionName,
                        excludedDeploymentId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    @Override
    public boolean hasSuccessfulDeploymentForBusinessVersion(Component component,
                                                              Environment environment,
                                                              String versionName,
                                                              UUID excludedDeploymentId) {
        return jpaDeploymentRepository.existsSuccessfulDeploymentForBusinessVersion(component, environment,
                versionName, excludedDeploymentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsCodeDeploymentForComponent(UUID componentId) {
        return jpaDeploymentRepository.existsCodeDeploymentForComponent(componentId);
    }

    @Override
    public List<Deployment> findDeploymentsWithJiraIssuesStartedAtOrAfter(ZonedDateTime startedAt) {
        return jpaDeploymentRepository.findDeploymentsWithJiraIssuesStartedAtOrAfter(startedAt);
    }

    @Override
    public List<Deployment> findCodeDeploymentsForJiraIssues(Set<String> normalizedIssueKeys) {
        if (normalizedIssueKeys.isEmpty()) {
            return List.of();
        }
        return jpaDeploymentRepository.findCodeDeploymentsForJiraIssues(normalizedIssueKeys);
    }

    @Override
    public List<Deployment> findDeploymentsForJiraIssues(Set<String> normalizedIssueKeys) {
        if (normalizedIssueKeys.isEmpty()) {
            return List.of();
        }
        return jpaDeploymentRepository.findDeploymentsForJiraIssues(normalizedIssueKeys);
    }
}
