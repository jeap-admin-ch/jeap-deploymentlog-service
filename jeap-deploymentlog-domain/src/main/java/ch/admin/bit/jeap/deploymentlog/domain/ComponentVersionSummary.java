package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;

public interface ComponentVersionSummary {
    String getComponentName();

    String getVersion();

    /**
     * Whether the component has since been undeployed from the environment. {@code false} for every row
     * returned unless the caller asked to have undeployed components included.
     */
    boolean getUndeployed();

    /**
     * When the component was undeployed, or {@code null} if it is still deployed.
     */
    ZonedDateTime getUndeployedAt();
}
