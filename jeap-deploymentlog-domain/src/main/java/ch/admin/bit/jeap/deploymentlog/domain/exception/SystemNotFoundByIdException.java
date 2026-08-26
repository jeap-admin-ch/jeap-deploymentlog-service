package ch.admin.bit.jeap.deploymentlog.domain.exception;

import java.util.UUID;

public class SystemNotFoundByIdException extends RuntimeException {

    public SystemNotFoundByIdException(UUID id) {
        super("No system found with id " + id);
    }
}
