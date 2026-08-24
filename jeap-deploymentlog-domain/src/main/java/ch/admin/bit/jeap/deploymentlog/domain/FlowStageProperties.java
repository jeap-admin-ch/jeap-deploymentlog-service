package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jeap.deploymentlog.flow", ignoreUnknownFields = false)
public class FlowStageProperties {

    private boolean enabled = true;
    private String startEnvironment;
    private String defaultFinalDeploymentEnvironment;
}
