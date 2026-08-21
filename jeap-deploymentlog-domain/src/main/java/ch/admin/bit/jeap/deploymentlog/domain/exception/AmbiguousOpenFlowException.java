package ch.admin.bit.jeap.deploymentlog.domain.exception;

import java.util.UUID;

public class AmbiguousOpenFlowException extends RuntimeException {

    public AmbiguousOpenFlowException(UUID componentId, String versionName, int count) {
        super("Found %d open flows for component %s and version '%s'"
                .formatted(count, componentId, versionName));
    }
}
