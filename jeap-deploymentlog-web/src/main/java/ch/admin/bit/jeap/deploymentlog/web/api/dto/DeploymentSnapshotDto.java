package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentComponentVersionState;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Value
@Builder
public class DeploymentSnapshotDto {

    String env;

    String version;

    ZonedDateTime deployedAt;

    public static List<DeploymentSnapshotDto> of(List<EnvironmentComponentVersionState> values) {
        return values.stream().map(v ->
                        DeploymentSnapshotDto.builder()
                                .env(v.getEnvironment().getName())
                                .version(v.getComponentVersion().getVersionName())
                                .deployedAt(v.getDeployment().getEndedAt())
                                .build())
                .collect(toList());
    }

}
