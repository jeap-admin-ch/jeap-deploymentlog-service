package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class SystemNotFoundException extends Exception {
    public SystemNotFoundException(String name) {
        super("No system found with name " + name);
    }
}
