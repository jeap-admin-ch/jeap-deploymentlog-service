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
                select flow.id
                from Flow flow
                where flow.componentVersion.component.id = page.componentId
            )
            order by
                case when page.cleanupAttemptedAt is null then 0 else 1 end,
                page.cleanupAttemptedAt,
                system.name,
                page.componentId
            """)
    List<ComponentPageCleanupCandidate> findCleanupCandidates(Pageable pageable);

    @Modifying
    @Query("""
            update ComponentPage page
            set page.cleanupAttemptedAt = :attemptedAt
            where page.componentId = :componentId
            and not exists (
                select flow.id
                from Flow flow
                where flow.componentVersion.component.id = :componentId
            )
            """)
    int markCleanupAttemptedIfNoFlow(@Param("componentId") UUID componentId,
                                     @Param("attemptedAt") ZonedDateTime attemptedAt);

    @Modifying
    @Query("""
            delete from ComponentPage page
            where page.componentId = :componentId
            and not exists (
                select flow.id
                from Flow flow
                where flow.componentVersion.component.id = :componentId
            )
            """)
    int deleteIfNoFlow(@Param("componentId") UUID componentId);
}
