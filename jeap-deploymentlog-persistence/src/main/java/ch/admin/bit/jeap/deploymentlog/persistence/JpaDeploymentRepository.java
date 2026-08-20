package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.SystemEnv;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaDeploymentRepository extends CrudRepository<Deployment, UUID> {

    Optional<Deployment> findByExternalId(String externalId);

    @Query("""
            select d from Deployment d, ComponentVersion cv, Component c, System s, Environment e \
            where e.id = :envId \
            and s.id = :systemId \
            and d.componentVersion.id = cv.id \
            and cv.component.id = c.id and c.system.id = s.id and d.environment.id = e.id
            """)
    List<Deployment> findAllDeploymentForSystemAndEnv(@Param("envId") UUID environmentId, @Param("systemId") UUID systemId);

    @Query("""
            select d from Deployment d, ComponentVersion cv, Component c, System s \
            where s.id = :systemId \
            and d.componentVersion.id = cv.id \
            and d.startedAt between :from and :to \
            and cv.component.id = c.id and c.system.id = s.id
            """)
    List<Deployment> findAllDeploymentsForSystemStartedBetween(@Param("systemId") UUID systemId, @Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);

    @Query("""
            select d from Deployment d, ComponentVersion cv, Component c, System s, Environment e \
            where e.id = :envId \
            and s.id = :systemId \
            and d.componentVersion.id = cv.id \
            and cv.component.id = c.id and c.system.id = s.id and d.environment.id = e.id \
            order by d.startedAt desc
            """)
    Page<Deployment> findDeploymentForSystemAndEnvLimited(@Param("envId") UUID environmentId, @Param("systemId") UUID systemId, Pageable pageable);

    @Query("""
            select d from Deployment d, ComponentVersion cv, Component c, System s, Environment e \
            where e.id = :envId \
            and d.startedAt > :minStartedAt \
            and d.componentVersion.id = cv.id \
            and cv.component.id = c.id and c.system.id = s.id and d.environment.id = e.id \
            order by d.startedAt desc
            """)
    Page<Deployment> findDeploymentForEnvLimited(@Param("envId") UUID environmentId, @Param("minStartedAt") ZonedDateTime minStartedAt, Pageable pageable);

    @Query("""
            select deployment.id from Deployment deployment \
            left join DeploymentPage page on deployment.id = page.deploymentId \
            where \
            deployment.startedAt >= :from and deployment.startedAt <= :to and \
            (page.id is null or deployment.lastModified > page.deploymentStateTimestamp)
            """)
    List<UUID> getDeploymentIdsMissingOrOutdatedGeneratedPages(@Param("from") ZonedDateTime from,
                                                               @Param("to") ZonedDateTime to, Pageable pageable);

    /**
     * Finds the system/environment combinations whose deployment history page has not been regenerated since the last
     * deployment. In contrast to the deployment letter page these pages are not tracked per deployment, so a docgen
     * run that stopped before reaching them leaves them outdated without any deployment being reported as missing a
     * page.
     * <p>
     * The system page is deliberately not part of the condition: it is written by the same run that writes the
     * deployment letter page, so a system page older than a deployment comes with a letter page that is outdated as
     * well - and that is what {@link #getDeploymentIdsMissingOrOutdatedGeneratedPages} already reports. Including it
     * here would only return the same system once per environment, as the condition does not depend on the
     * environment.
     */
    @Query("""
            select distinct new ch.admin.bit.jeap.deploymentlog.domain.SystemEnv(\
            system.id, system.name, environment.id) \
            from Deployment deployment \
            join deployment.componentVersion componentVersion \
            join componentVersion.component component \
            join component.system system \
            join deployment.environment environment \
            left join EnvironmentHistoryPage historyPage \
            on historyPage.systemId = system.id and historyPage.environmentId = environment.id \
            where \
            deployment.startedAt >= :from and deployment.startedAt <= :to and \
            (historyPage.id is null or deployment.lastModified > historyPage.lastUpdatedAt) \
            order by system.name, environment.id
            """)
    List<SystemEnv> getSystemEnvsWithOutdatedAggregatePages(@Param("from") ZonedDateTime from,
                                                            @Param("to") ZonedDateTime to, Pageable pageable);

    @Query("""
            select count(deployment.id) from Deployment deployment \
            left join DeploymentPage page on deployment.id = page.deploymentId \
            where \
            deployment.startedAt >= :from and \
            (page.id is null or deployment.lastModified > page.deploymentStateTimestamp)
            """)
    long countDeploymentsWithMissingOrOutdatedGeneratedPages(@Param("from") ZonedDateTime from);

    @Query("""
            select d from Deployment d \
            where d.componentVersion.component = :component \
            and d.environment = :env \
            order by d.startedAt desc
            """)
    List<Deployment> getLastDeploymentsForComponent(@Param("component") Component component,
                                                    @Param("env") Environment env,
                                                    Pageable pageable);

    @Query("""
            select d from Deployment d \
            where d.componentVersion.component = :component \
            and d.environment = :env \
            and d.state = 'SUCCESS' \
            order by d.startedAt desc
            """)
    List<Deployment> getLastSuccessfulDeploymentsForComponent(@Param("component") Component component,
                                                              @Param("env") Environment env,
                                                              Pageable pageable);

    @Query("""
            select d from Deployment d \
            where d.componentVersion.component = :component \
            and d.componentVersion.versionName <> :version \
            and d.environment = :env \
            and d.state = 'SUCCESS' \
            order by d.startedAt desc
            """)
    List<Deployment> getSuccessfulDeploymentsForComponentDifferentToVersion(@Param("component") Component component,
                                                               @Param("env") Environment env,
                                                               @Param("version") String version,
                                                               Pageable pageable);

    @Query("""
            select s.name from Deployment d, ComponentVersion cv, Component c, System s \
            where d.id = :deploymentId \
            and cv = d.componentVersion \
            and c = cv.component \
            and s = c.system
            """)
    String getSystemNameForDeployment(@Param("deploymentId") UUID deploymentId);
}
