package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnit;
import ch.admin.bit.jeap.deploymentlog.domain.VersionNumber;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.UUID;

@Value
@Builder
public class ComponentVersionDto {

    UUID id;

    String versionName;

    ZonedDateTime taggedAt;

    VersionNumber versionNumber;

    String minorVersion;

    String patchVersion;

    String buildVersion;

    String versionControlUrl;

    String commitRef;

    ZonedDateTime committedAt;

    boolean publishedVersion;

    DeploymentUnit deploymentUnit;

    ComponentDto component;

    static ComponentVersionDto of(ComponentVersion componentVersion) {
        return ComponentVersionDto.builder()
                .id(componentVersion.getId())
                .versionName(componentVersion.getVersionName())
                .taggedAt(componentVersion.getTaggedAt())
                .versionNumber(componentVersion.getVersionNumber())
                .versionControlUrl(componentVersion.getVersionControlUrl())
                .commitRef(componentVersion.getCommitRef())
                .committedAt(componentVersion.getCommittedAt())
                .publishedVersion(componentVersion.isPublishedVersion())
                .deploymentUnit(componentVersion.getDeploymentUnit())
                .component(ComponentDto.of(componentVersion.getComponent()))
                .build();
    }

}
