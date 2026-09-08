package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record DataRetentionCandidate(UUID deploymentId, UUID retentionUnitId, String systemName) {

    public static DataRetentionCandidate standalone(UUID deploymentId, String systemName) {
        return new DataRetentionCandidate(deploymentId, deploymentId, systemName);
    }
}
