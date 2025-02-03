package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DeploymentHistoryOverviewPageDto {

    String environmentName;
    Integer deploymentHistoryMaxShow;
    String deploymentHistoryOverviewMinStartedAt;
    List<DeploymentDto> deployments;

    public String getPageTitle() {
        return "Deployment History Overview " + environmentName;
    }

}
