package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class UndeploymentCreateDto {

    ZonedDateTime startedAt;

    String startedBy;

    String systemName;

    String componentName;

    String environmentName;

    String remedyChangeId;

    public String getEnvironmentName() {
        return environmentName != null ? environmentName.toUpperCase() : null;
    }
}
