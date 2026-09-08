package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataRetentionRepository {

    List<DataRetentionCandidate> findDeletionCandidates(ZonedDateTime cutoff, int limit);

    DataRetentionResult deleteCandidates(Collection<UUID> deploymentIds, ZonedDateTime cutoff);

    List<DataRetentionRefreshTask> findPendingRefreshTasks(int limit);

    void deletePendingRefreshTask(UUID refreshTaskId);
}
