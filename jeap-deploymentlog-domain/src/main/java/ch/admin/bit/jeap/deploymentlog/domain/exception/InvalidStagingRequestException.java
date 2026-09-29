package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class InvalidStagingRequestException extends RuntimeException {

    public InvalidStagingRequestException(String message) {
        super(message);
    }
}
