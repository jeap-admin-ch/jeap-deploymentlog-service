package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Set;
import java.util.UUID;

public record DataRetentionResult(
        Set<SystemEnv> systemEnvironments,
        Set<UUID> componentIds,
        Set<UUID> environmentIds,
        Set<String> jiraIssueKeys,
        int deletedDeployments,
        int deletedFlows,
        Set<UUID> deletedDeploymentIds) {

    public static DataRetentionResult empty() {
        return new DataRetentionResult(Set.of(), Set.of(), Set.of(), Set.of(), 0, 0, Set.of());
    }

    public boolean isEmpty() {
        return deletedDeployments == 0;
    }
}
