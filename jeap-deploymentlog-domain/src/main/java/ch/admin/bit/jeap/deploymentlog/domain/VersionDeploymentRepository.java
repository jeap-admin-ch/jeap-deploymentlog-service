package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.UUID;

public interface VersionDeploymentRepository {
    List<List<Deployment>> findLatestVersions(UUID componentId, int limit);
    boolean existsForComponent(UUID componentId);
    void lockComponent(UUID componentId);
    List<StagingHistoryEntry> history(UUID componentId);
    List<StagingMetricValue> metrics(String startEnvironment, String endEnvironment);
}
