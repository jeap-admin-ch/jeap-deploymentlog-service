package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class AliasNameAlreadyDefinedException extends Exception {

    private AliasNameAlreadyDefinedException(String message) {
        super(message);
    }

    public static AliasNameAlreadyDefinedException aliasNameAlreadyDefined(String name) {
        return new AliasNameAlreadyDefinedException("The alias '" + name + "' is already defined");
    }

}
