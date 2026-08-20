package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DeploymentHistoryPageDto {

    String environmentName;
    String systemName;
    Integer deploymentHistoryMaxShow;
    List<DeploymentDto> deployments;

    public static String pageTitle(String systemName, String environmentName) {
        return "Deployment History " + environmentName + " (" + systemName + ")";
    }
}
