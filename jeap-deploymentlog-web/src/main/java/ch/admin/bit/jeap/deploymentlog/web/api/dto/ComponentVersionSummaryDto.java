package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersionSummary;

public record ComponentVersionSummaryDto(String componentName, String version) {
    public static ComponentVersionSummaryDto from(ComponentVersionSummary componentVersionSummary) {
        return new ComponentVersionSummaryDto(componentVersionSummary.getComponentName(), componentVersionSummary.getVersion());
    }
}
