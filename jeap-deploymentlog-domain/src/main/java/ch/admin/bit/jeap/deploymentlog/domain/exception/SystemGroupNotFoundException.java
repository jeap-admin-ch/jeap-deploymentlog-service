package ch.admin.bit.jeap.deploymentlog.domain.exception;

import java.util.UUID;

public class SystemGroupNotFoundException extends RuntimeException {

    public SystemGroupNotFoundException(UUID id) {
        super("No system group found with id " + id);
    }
}
