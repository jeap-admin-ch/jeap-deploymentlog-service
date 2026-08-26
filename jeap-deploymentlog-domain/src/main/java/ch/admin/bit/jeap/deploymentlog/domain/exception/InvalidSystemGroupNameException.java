package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class InvalidSystemGroupNameException extends RuntimeException {

    public InvalidSystemGroupNameException() {
        super("System group name must not be blank");
    }
}
