package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class SystemGroupNameAlreadyExistsException extends RuntimeException {

    public SystemGroupNameAlreadyExistsException(String name) {
        super("A system group with name '%s' already exists".formatted(name));
    }

    public SystemGroupNameAlreadyExistsException(String name, Throwable cause) {
        super("A system group with name '%s' already exists".formatted(name), cause);
    }
}
