package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record DataRetentionRefreshTask(UUID id, DataRetentionResult result) {

    public static DataRetentionRefreshTask from(DataRetentionResult result) {
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a refresh task for an empty retention result");
        }
        return new DataRetentionRefreshTask(UUID.randomUUID(), result);
    }
}
