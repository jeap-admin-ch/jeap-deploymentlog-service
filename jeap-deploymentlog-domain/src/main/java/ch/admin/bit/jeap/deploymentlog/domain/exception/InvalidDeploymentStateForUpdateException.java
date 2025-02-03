package ch.admin.bit.jeap.deploymentlog.domain.exception;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;

public class InvalidDeploymentStateForUpdateException extends Exception {

    public InvalidDeploymentStateForUpdateException(DeploymentState state) {
        super("The provided state '" + state + "' is invalid for a state update");
    }
}
