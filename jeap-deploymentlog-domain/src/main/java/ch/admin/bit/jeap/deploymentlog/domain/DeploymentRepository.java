package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Interface to be implemented by a persistence provider to access @{@link Deployment}s
 */
public interface DeploymentRepository {

    boolean isPageGenerationRepairRequired(UUID deploymentId);

    Optional<UUID> getPageGenerationRequestId(UUID deploymentId);

    void completePageGenerationRequest(UUID deploymentId, UUID requestId);

    void resumePageGeneration(UUID deploymentId);

    void suppressPageGeneration(UUID deploymentId, ZonedDateTime pageStateTimestamp);

    void classifyLegacyPageGeneration(boolean housekeepingEnabled, ZonedDateTime cutoff, int keepPerEnvironment);

    Deployment save(Deployment deployment);

    Optional<Deployment> findByExternalId(String externalId);

    Optional<Deployment> findByExternalIdForUpdate(String externalId);

    List<Deployment> findAllDeploymentForSystemAndEnv(System system, Environment environment);

    List<Deployment> findAllDeploymentsForSystemStartedBetween(System system, ZonedDateTime from, ZonedDateTime to);

    List<Integer> findAllDeploymentsYearsForSystemAndEnv(System system, Environment environment);

    List<Deployment> findDeploymentForSystemAndEnvLimited(System system, Environment environment, int maxShow);

    List<Deployment> findDeploymentForEnvLimited(Environment environment, ZonedDateTime minStartedAt, int limit);

    Deployment getById(UUID deploymentId);

    Optional<Deployment> findById(UUID deploymentId);

    List<UUID> getDeploymentIdsWithMissingOrOutdatedGeneratedPages(int limit, ZonedDateTime from, ZonedDateTime to);

    default List<UUID> getDeploymentIdsWithMissingOrOutdatedGeneratedPages(int limit, ZonedDateTime to) {
        return getDeploymentIdsWithMissingOrOutdatedGeneratedPages(
                limit, ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC), to);
    }

    void markPageGenerationAttempted(List<UUID> deploymentIds, ZonedDateTime attemptedAt);

    long countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime from);

    Optional<Deployment> getLastDeploymentForComponent(Component component, Environment env);

    Optional<Deployment> getLastSuccessfulDeploymentForComponent(Component component, Environment env);

    Optional<Deployment> getLastSuccessfulCodeDeploymentForComponentDifferentToVersion(Component component, Environment env, String version);

    String getSystemNameForDeployment(UUID deploymentId);

    String getComponentNameForDeployment(UUID deploymentId);

    Optional<Deployment> getLastDeploymentForBusinessVersion(Component component,
                                                              Environment environment,
                                                              String versionName,
                                                              UUID excludedDeploymentId);

    boolean hasSuccessfulDeploymentForBusinessVersion(Component component,
                                                       Environment environment,
                                                       String versionName,
                                                       UUID excludedDeploymentId);

    List<Deployment> findDeploymentsWithJiraIssuesStartedAtOrAfter(ZonedDateTime startedAt);

    List<Deployment> findCodeDeploymentsForJiraIssues(Set<String> normalizedIssueKeys);

    List<Deployment> findDeploymentsForJiraIssues(Set<String> normalizedIssueKeys);
}
