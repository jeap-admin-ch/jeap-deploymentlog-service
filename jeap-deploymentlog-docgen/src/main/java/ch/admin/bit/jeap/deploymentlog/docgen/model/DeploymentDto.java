package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeploymentDto {

    public String getComponentPageTitle() {
        return ComponentPageDto.pageTitle(component, system);
    }

    String startedAt;
    String duration;
    String deploymentId;
    String deploymentLetterLink;
    String component;
    String system;
    String version;
    String versionControlUrl;
    String startedBy;
    String state;
    String deploymentTypes;
}
