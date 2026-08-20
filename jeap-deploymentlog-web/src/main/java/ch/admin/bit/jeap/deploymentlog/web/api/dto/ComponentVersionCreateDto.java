package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class ComponentVersionCreateDto {

    String versionName;

    ZonedDateTime taggedAt;

    String versionControlUrl;

    String commitRef;

    @NotNull(message = "componentVersion.committedAt is required")
    @JsonAlias("commitedAt")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Commit timestamp of the component version. Used to determine the age of versions.",
            example = "2026-08-20T10:15:30+02:00")
    ZonedDateTime committedAt;

    boolean publishedVersion;

    String componentName;

    String systemName;

}
