package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.Value;

import java.util.UUID;

@Value
public class SystemEnv {
    UUID systemId;
    String systemName;
    UUID envId;

    static SystemEnv from(Deployment deployment) {
        return new SystemEnv(
                deployment.getComponentVersion().getComponent().getSystem().getId(),
                deployment.getComponentVersion().getComponent().getSystem().getName(),
                deployment.getEnvironment().getId());
    }
}
