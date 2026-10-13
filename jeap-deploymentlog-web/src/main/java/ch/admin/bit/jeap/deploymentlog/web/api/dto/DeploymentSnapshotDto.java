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

    /**
     * Whether the component has since been undeployed from this environment. The snapshot is then the
     * undeployment, so {@link #version} and {@link #deployedAt} are {@code null}.
     */
    boolean undeployed;

    /**
     * When the component was undeployed from this environment, or {@code null} if it is still deployed.
     */
    ZonedDateTime undeployedAt;

    public static List<DeploymentSnapshotDto> of(List<EnvironmentComponentVersionState> values) {
        return values.stream().map(v ->
                        DeploymentSnapshotDto.builder()
                                .env(v.getEnvironment().getName())
                                .version(v.isUndeployed() ? null : v.getComponentVersion().getVersionName())
                                .deployedAt(v.getDeployment().getEndedAt())
                                .undeployed(v.isUndeployed())
                                .undeployedAt(v.isUndeployed() ? v.getDeployment().getStartedAt() : null)
                                .build())
                .collect(toList());
    }

}
