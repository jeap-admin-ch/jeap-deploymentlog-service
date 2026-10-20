package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraChangeViewRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

interface JpaJiraChangeViewRequestRepository extends CrudRepository<JiraChangeViewRequest, UUID> {

    List<JiraChangeViewRequest> findByProjectKey(String projectKey);

    @Query("""
            select r.projectKey from JiraChangeViewRequest r
            where r.requestedAt < :requestedBefore
            group by r.projectKey
            order by min(r.requestedAt), r.projectKey
            """)
    List<String> findProjectKeysRequestedBefore(@Param("requestedBefore") ZonedDateTime requestedBefore,
                                                Pageable pageable);

    @Query("select count(distinct r.issueKey) from JiraChangeViewRequest r where r.requestedAt < :requestedBefore")
    long countIssueKeysRequestedBefore(@Param("requestedBefore") ZonedDateTime requestedBefore);
}
