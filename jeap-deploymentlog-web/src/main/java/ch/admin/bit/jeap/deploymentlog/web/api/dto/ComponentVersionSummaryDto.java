package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersionSummary;

import java.time.ZonedDateTime;

public record ComponentVersionSummaryDto(String componentName, String version, boolean undeployed, ZonedDateTime undeployedAt) {
    public static ComponentVersionSummaryDto from(ComponentVersionSummary componentVersionSummary) {
        return new ComponentVersionSummaryDto(componentVersionSummary.getComponentName(), componentVersionSummary.getVersion(),
                componentVersionSummary.getUndeployed(), componentVersionSummary.getUndeployedAt());
    }
}
