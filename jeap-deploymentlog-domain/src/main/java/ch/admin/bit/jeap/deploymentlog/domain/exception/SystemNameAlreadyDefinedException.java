package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class SystemNameAlreadyDefinedException extends Exception {

    private SystemNameAlreadyDefinedException(String message) {
        super(message);
    }

    public static SystemNameAlreadyDefinedException systemNameAlreadyDefined(String name) {
        return new SystemNameAlreadyDefinedException("The system '" + name + "' is already defined");
    }
}
