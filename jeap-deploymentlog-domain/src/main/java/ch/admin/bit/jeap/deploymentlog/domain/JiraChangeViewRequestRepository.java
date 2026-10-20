package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JiraChangeViewRequestRepository {

    void saveAll(Collection<JiraChangeViewRequest> requests);

    List<JiraChangeViewRequest> findByProjectKey(String projectKey);

    void deleteAllById(Collection<UUID> ids);

    /**
     * Project keys with at least one request older than the given timestamp, oldest request first.
     */
    List<String> findProjectKeysRequestedBefore(ZonedDateTime requestedBefore, int limit);

    long countIssueKeysRequestedBefore(ZonedDateTime requestedBefore);
}
