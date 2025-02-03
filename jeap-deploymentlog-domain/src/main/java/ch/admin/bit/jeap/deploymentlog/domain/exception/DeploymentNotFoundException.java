package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class DeploymentNotFoundException extends Exception {

    public DeploymentNotFoundException(String externalId) {
        super("No Deployment found with externalId " + externalId);
    }
}
