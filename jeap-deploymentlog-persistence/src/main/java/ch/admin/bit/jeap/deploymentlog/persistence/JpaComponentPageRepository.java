package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageCleanupCandidate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

interface JpaComponentPageRepository extends CrudRepository<ComponentPage, UUID> {
    List<ComponentPage> findByParentPageId(String parentPageId);

    @Query("""
            select new ch.admin.bit.jeap.deploymentlog.domain.ComponentPageCleanupCandidate(
                page.componentId, page.pageId, system.name)
            from ComponentPage page
            join Component component on component.id = page.componentId
            join component.system system
            where not exists (
                select deployment.id
                from Deployment deployment
                where deployment.componentVersion.component.id = page.componentId and deployment.environment.name in :stages
                and ch.admin.bit.jeap.deploymentlog.domain.DeploymentType.CODE member of deployment.deploymentTypes
                and deployment.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
            )
            order by
                case when page.cleanupAttemptedAt is null then 0 else 1 end,
                page.cleanupAttemptedAt,
                system.name,
                page.componentId
            """)
    List<ComponentPageCleanupCandidate> findCleanupCandidates(@Param("stages") List<String> stages, Pageable pageable);

    @Modifying
    @Query("""
            update ComponentPage page
            set page.cleanupAttemptedAt = :attemptedAt
            where page.componentId = :componentId
            and not exists (
                select deployment.id
                from Deployment deployment
                where deployment.componentVersion.component.id = :componentId and deployment.environment.name in :stages
                and ch.admin.bit.jeap.deploymentlog.domain.DeploymentType.CODE member of deployment.deploymentTypes
                and deployment.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
            )
            """)
    int markCleanupAttemptedIfNoRelevantDeployment(@Param("componentId") UUID componentId,
                                     @Param("attemptedAt") ZonedDateTime attemptedAt, @Param("stages") List<String> stages);

    @Modifying
    @Query("""
            delete from ComponentPage page
            where page.componentId = :componentId
            and not exists (
                select deployment.id
                from Deployment deployment
                where deployment.componentVersion.component.id = :componentId and deployment.environment.name in :stages
                and ch.admin.bit.jeap.deploymentlog.domain.DeploymentType.CODE member of deployment.deploymentTypes
                and deployment.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
            )
            """)
    int deleteIfNoRelevantDeployment(@Param("componentId") UUID componentId, @Param("stages") List<String> stages);
}
