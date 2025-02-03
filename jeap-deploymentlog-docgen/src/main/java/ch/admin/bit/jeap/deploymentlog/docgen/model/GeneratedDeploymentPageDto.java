package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeneratedDeploymentPageDto {

    DeploymentLetterPageDto deploymentLetterPageDto;
    String pageId;
}
