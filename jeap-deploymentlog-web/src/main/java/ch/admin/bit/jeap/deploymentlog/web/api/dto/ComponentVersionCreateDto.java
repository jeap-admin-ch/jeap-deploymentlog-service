package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class ComponentVersionCreateDto {

    String versionName;

    ZonedDateTime taggedAt;

    String versionControlUrl;

    String commitRef;

    ZonedDateTime commitedAt;

    boolean publishedVersion;

    String componentName;

    String systemName;

}
