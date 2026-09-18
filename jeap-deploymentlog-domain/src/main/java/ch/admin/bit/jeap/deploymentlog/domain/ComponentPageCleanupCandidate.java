package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.UUID;

public record ComponentPageCleanupCandidate(UUID componentId, String pageId, String systemName) {
}
