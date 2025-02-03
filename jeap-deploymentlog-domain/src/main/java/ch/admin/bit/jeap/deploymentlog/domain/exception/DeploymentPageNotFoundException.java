package ch.admin.bit.jeap.deploymentlog.domain.exception;

public class DeploymentPageNotFoundException extends Exception {

    public DeploymentPageNotFoundException(String externalId) {
        super("No Deployment page found for deployment with externalId " + externalId);
    }
}
