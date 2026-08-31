package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class SystemNotFoundForGroupException extends RuntimeException {

    public SystemNotFoundForGroupException(String systemName) {
        super("No system found with name " + systemName);
    }
}
