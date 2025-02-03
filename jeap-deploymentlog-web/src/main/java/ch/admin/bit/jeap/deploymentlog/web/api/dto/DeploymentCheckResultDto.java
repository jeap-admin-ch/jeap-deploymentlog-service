package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeploymentCheckResultDto {
    DeploymentCheckResult result;
    String message;
}
