package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface to be implemented by a persistence provider to access @{@link Deployment}s
 */
public interface DeploymentRepository {

    Deployment save(Deployment deployment);

    Optional<Deployment> findByExternalId(String externalId);

    List<Deployment> findAllDeploymentForSystemAndEnv(System system, Environment environment);

    List<Deployment> findAllDeploymentsForSystemStartedBetween(System system, ZonedDateTime from, ZonedDateTime to);

    List<Integer> findAllDeploymentsYearsForSystemAndEnv(System system, Environment environment);

    List<Deployment> findDeploymentForSystemAndEnvLimited(System system, Environment environment, int maxShow);

    List<Deployment> findDeploymentForEnvLimited(Environment environment, ZonedDateTime minStartedAt, int limit);

    Deployment getById(UUID deploymentId);

    Optional<Deployment> findById(UUID deploymentId);

    List<UUID> getDeploymentIdsWithMissingOrOutdatedGeneratedPages(int limit, ZonedDateTime from, ZonedDateTime to);

    long countDeploymentsWithMissingOrOutdatedGeneratedPages(ZonedDateTime from);

    Optional<Deployment> getLastDeploymentForComponent(Component component, Environment env);

    Optional<Deployment> getLastSuccessfulDeploymentForComponent(Component component, Environment env);

    Optional<Deployment> getLastSuccessfulDeploymentForComponentDifferentToVersion(Component component, Environment env, String version);

    String getSystemNameForDeployment(UUID deploymentId);
}
