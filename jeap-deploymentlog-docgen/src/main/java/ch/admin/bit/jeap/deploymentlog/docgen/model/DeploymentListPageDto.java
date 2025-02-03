package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Value;

@Value
public class DeploymentListPageDto {

    String pageTitle;

    public DeploymentListPageDto(String environmentName, String systemName, int year) {
        pageTitle = year + "-Deployments " + environmentName + " (" + systemName + ")";
    }
}
