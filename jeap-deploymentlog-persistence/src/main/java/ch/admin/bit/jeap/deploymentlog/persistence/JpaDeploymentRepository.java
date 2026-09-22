package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

@Repository
interface JpaDeploymentRepository extends CrudRepository<Deployment, UUID> {

    @Query("""
            select count(d) > 0 from Deployment d left join DeploymentPage p on d.id = p.deploymentId
            where d.id = :id and d.pageGenerationSuppressed = false and d.pageGenerationLegacyUnclassified = false
            and (p.id is null or d.lastModified > p.deploymentStateTimestamp or d.pageGenerationRequestId is not null)
            """)
    boolean isPageGenerationRepairRequired(@Param("id") UUID deploymentId);

    @Query("select d.pageGenerationRequestId from Deployment d where d.id = :id")
    Optional<UUID> getPageGenerationRequestId(@Param("id") UUID deploymentId);

    @Modifying
    @Query("update Deployment d set d.pageGenerationRequestId = null where d.id = :id and d.pageGenerationRequestId = :requestId")
    void completePageGenerationRequest(@Param("id") UUID deploymentId, @Param("requestId") UUID requestId);

    @Modifying
    @Query("update Deployment d set d.pageGenerationSuppressed = false, d.pageGenerationLegacyUnclassified = false, " +
            "d.pageGenerationRequestId = :requestId where d.id = :id")
    void resumePageGeneration(@Param("id") UUID deploymentId, @Param("requestId") UUID requestId);

    @Modifying
    @Query("update Deployment d set d.pageGenerationSuppressed = true where d.id = :id " +
            "and d.lastModified <= :pageStateTimestamp and d.pageGenerationRequestId is null")
    void suppressPageGeneration(@Param("id") UUID deploymentId,
                                @Param("pageStateTimestamp") ZonedDateTime pageStateTimestamp);

    @Query("""
            select d.id from Deployment d
            where d.pageGenerationLegacyUnclassified = true and d.pageGenerationRequestId is null
            and d.startedAt between :from and :to
            order by d.startedAt, d.id
            """)
    List<UUID> findLegacyPageGenerationIds(@Param("from") ZonedDateTime from,
                                           @Param("to") ZonedDateTime to,
                                           Pageable pageable);

    @Modifying
    @Query("update Deployment d set d.pageGenerationLegacyUnclassified = false where d.id in :ids")
    void releaseLegacyPageGeneration(@Param("ids") List<UUID> deploymentIds);

    Optional<Deployment> findByExternalId(String externalId);

    @Query(value = "select deployment.* from deployment where deployment.external_id = :externalId for update",
            nativeQuery = true)
    Optional<Deployment> findByExternalIdForUpdate(@Param("externalId") String externalId);

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
            deployment.pageGenerationSuppressed = false and deployment.pageGenerationLegacyUnclassified = false and \
            (deployment.startedAt >= :from or deployment.pageGenerationRequestId is not null) and deployment.startedAt <= :to and \
            (page.id is null or deployment.lastModified > page.deploymentStateTimestamp or deployment.pageGenerationRequestId is not null) \
            order by deployment.pageGenerationAttemptedAt asc nulls first, deployment.startedAt asc, deployment.id asc
            """)
    List<UUID> getDeploymentIdsMissingOrOutdatedGeneratedPages(@Param("from") ZonedDateTime from,
                                                               @Param("to") ZonedDateTime to, Pageable pageable);

    @Query("""
            select deployment.id from Deployment deployment \
            left join DeploymentPage page on deployment.id = page.deploymentId \
            where deployment.pageGenerationSuppressed = false and deployment.pageGenerationLegacyUnclassified = false \
            and deployment.startedAt <= :to and \
            (page.id is null or deployment.lastModified > page.deploymentStateTimestamp or deployment.pageGenerationRequestId is not null) \
            order by deployment.pageGenerationAttemptedAt asc nulls first, deployment.startedAt asc, deployment.id asc
            """)
    List<UUID> getAllDeploymentIdsMissingOrOutdatedGeneratedPages(@Param("to") ZonedDateTime to, Pageable pageable);

    @Modifying
    @Query("""
            update Deployment deployment \
            set deployment.pageGenerationAttemptedAt = :attemptedAt \
            where deployment.id in :deploymentIds
            """)
    void markPageGenerationAttempted(@Param("deploymentIds") List<UUID> deploymentIds,
                                     @Param("attemptedAt") ZonedDateTime attemptedAt);

    @Query("""
            select count(deployment.id) from Deployment deployment \
            left join DeploymentPage page on deployment.id = page.deploymentId \
            where \
            deployment.pageGenerationSuppressed = false and deployment.pageGenerationLegacyUnclassified = false and \
            deployment.startedAt >= :from and \
            (page.id is null or deployment.lastModified > page.deploymentStateTimestamp or deployment.pageGenerationRequestId is not null)
            """)
    long countDeploymentsWithMissingOrOutdatedGeneratedPages(@Param("from") ZonedDateTime from);

    @Query("""
            select distinct new ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity(
                system.name, component.name, environment.name, deploymentType)
            from Deployment deployment
            join deployment.componentVersion componentVersion
            join componentVersion.component component
            join component.system system
            join deployment.environment environment
            join deployment.deploymentTypes deploymentType
            where deployment.state = :state
            """)
    List<DeploymentMetricIdentity> findMetricIdentitiesByState(@Param("state") DeploymentState state);

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
            left join d.deploymentTypes deploymentType \
            where d.componentVersion.component = :component \
            and d.componentVersion.versionName <> :version \
            and d.environment = :env \
            and d.state = 'SUCCESS' \
            and d.sequence <> 'UNDEPLOYED' \
            and (deploymentType = 'CODE' or deploymentType is null) \
            order by d.startedAt desc
            """)
    List<Deployment> getSuccessfulCodeDeploymentsForComponentDifferentToVersion(@Param("component") Component component,
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

    @Query("""
            select c.name from Deployment d, ComponentVersion cv, Component c
            where d.id = :deploymentId
            and cv = d.componentVersion
            and c = cv.component
            """)
    String getComponentNameForDeployment(@Param("deploymentId") UUID deploymentId);

    @Query("""
            select d from Deployment d
            where d.componentVersion.component = :component
            and d.environment = :environment
            and d.componentVersion.versionName = :versionName
            and d.id <> :excludedDeploymentId
            order by d.startedAt desc, d.id desc
            """)
    List<Deployment> findLastDeploymentForBusinessVersion(@Param("component") Component component,
                                                          @Param("environment") Environment environment,
                                                          @Param("versionName") String versionName,
                                                          @Param("excludedDeploymentId") UUID excludedDeploymentId,
                                                          Pageable pageable);

    @Query("""
            select count(d) > 0 from Deployment d
            where d.componentVersion.component = :component
            and d.environment = :environment
            and d.componentVersion.versionName = :versionName
            and d.id <> :excludedDeploymentId
            and d.state = 'SUCCESS'
            """)
    boolean existsSuccessfulDeploymentForBusinessVersion(@Param("component") Component component,
                                                          @Param("environment") Environment environment,
                                                          @Param("versionName") String versionName,
                                                          @Param("excludedDeploymentId") UUID excludedDeploymentId);

    @Query(value = """
            select exists (
                select 1
                from deployment d
                join component_version cv on cv.id = d.component_version_id
                join deployment_types dt on dt.deployment_id = d.id
                where cv.component_id = :componentId
                and dt.type = 'CODE'
            )
            """, nativeQuery = true)
    boolean existsCodeDeploymentForComponent(@Param("componentId") UUID componentId);

    @Query("""
            select distinct d from Deployment d
            join d.changelog c
            join c.jiraIssueKeys issueKey
            where d.startedAt >= :startedAt
            """)
    List<Deployment> findDeploymentsWithJiraIssuesStartedAtOrAfter(@Param("startedAt") ZonedDateTime startedAt);

    @Query("""
            select distinct d from Deployment d
            join d.changelog c
            join c.jiraIssueKeys issueKey
            join d.deploymentTypes deploymentType
            where deploymentType = 'CODE'
            and upper(trim(issueKey)) in :issueKeys
            """)
    List<Deployment> findCodeDeploymentsForJiraIssues(@Param("issueKeys") Set<String> normalizedIssueKeys);

    @Query("""
            select distinct d from Deployment d
            join d.changelog c
            join c.jiraIssueKeys issueKey
            where upper(trim(issueKey)) in :issueKeys
            """)
    List<Deployment> findDeploymentsForJiraIssues(@Param("issueKeys") Set<String> normalizedIssueKeys);
}
