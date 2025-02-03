package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class ComponentNotFoundException extends Exception {
    public ComponentNotFoundException(String systemName, String componentName) {
        super("No component with name " + componentName + " found in system " + systemName);
    }
}
