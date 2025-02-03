package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class EnvironmentNotFoundException extends Exception {
    public EnvironmentNotFoundException(String name) {
        super("No environment found with name " + name);
    }
}
