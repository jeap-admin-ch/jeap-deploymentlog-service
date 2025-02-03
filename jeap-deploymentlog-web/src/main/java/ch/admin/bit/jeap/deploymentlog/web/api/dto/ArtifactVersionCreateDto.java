package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Data;

@Data
public class ArtifactVersionCreateDto {

    String coordinates;

    String buildJobLink;

}
