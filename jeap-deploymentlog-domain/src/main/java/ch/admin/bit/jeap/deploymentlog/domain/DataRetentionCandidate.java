package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record DataRetentionCandidate(UUID deploymentId, String systemName) {
}
