package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
public class DeploymentUpdateStateDto {

    ZonedDateTime timestamp;

    DeploymentState state;

    String message;

    Map<String, String> properties;

    public Map<String, String> getProperties() {
        return properties == null ? Map.of() : properties;
    }
}

