package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Value
@Builder
public class DeploymentDto {

    UUID id;

    String externalId;

    ZonedDateTime startedAt;

    ZonedDateTime endedAt;

    DeploymentState state;

    String startedBy;

    EnvironmentDto environment;

    ComponentVersionDto componentVersion;

    Set<LinkDto> links;

    Map<String, String> properties;

    public static DeploymentDto of(Deployment deployment) {
        return DeploymentDto.builder()
                .id(deployment.getId())
                .externalId(deployment.getExternalId())
                .startedAt(deployment.getStartedAt())
                .endedAt(deployment.getEndedAt())
                .state(deployment.getState())
                .startedBy(deployment.getStartedBy())
                .environment(EnvironmentDto.of(deployment.getEnvironment()))
                .componentVersion(ComponentVersionDto.of(deployment.getComponentVersion()))
                .links(LinkDto.allOf(deployment.getLinks()))
                .properties(new TreeMap<>(deployment.getProperties()))
                .build();
    }

}
