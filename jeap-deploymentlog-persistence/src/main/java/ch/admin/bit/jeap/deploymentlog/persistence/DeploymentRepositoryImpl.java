package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class DeploymentRepositoryImpl implements DeploymentRepository {

    private final JpaDeploymentRepository jpaDeploymentRepository;

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
