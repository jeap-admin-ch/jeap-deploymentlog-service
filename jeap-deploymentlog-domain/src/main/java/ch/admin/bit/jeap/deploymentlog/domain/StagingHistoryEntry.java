package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

public record StagingHistoryEntry(UUID deploymentId, String version, String environment,
                                  ZonedDateTime startedAt, ZonedDateTime endedAt,
                                  DeploymentState state, boolean undeployment) {
}
