package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
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
