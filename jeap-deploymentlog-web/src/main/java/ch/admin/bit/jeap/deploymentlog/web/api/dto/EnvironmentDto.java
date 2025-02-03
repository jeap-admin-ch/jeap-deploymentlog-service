package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
@Schema(description = "Number of alerts in a Service")
public class EnvironmentDto {

    UUID id;

    String name;

    int stagingOrder;

    boolean productive;

    static EnvironmentDto of(Environment environment) {
        return EnvironmentDto.builder()
                .id(environment.getId())
                .name(environment.getName())
                .stagingOrder(environment.getStagingOrder())
                .productive(environment.isProductive())
                .build();
    }

}
