package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.*;

import java.time.ZonedDateTime;
import java.util.UUID;

public class TestDataFactory {

    private TestDataFactory() {
    }

    public static Deployment createDeployment(Environment environment, Component component, ZonedDateTime startedAt, DeploymentTarget deploymentTarget) {
        return createDeployment(environment, component, startedAt, "1.2.3-4", deploymentTarget);
    }

    public static Deployment createDeployment(Environment environment, Component component, ZonedDateTime startedAt, String versionName, DeploymentTarget deploymentTarget) {
        ComponentVersion componentVersion = ComponentVersion.builder()
                .commitRef("test")
                .taggedAt(ZonedDateTime.now())
                .committedAt(ZonedDateTime.now())
                .versionControlUrl("test")
                .publishedVersion(false)
                .component(component)
                .versionName(versionName)
                .deploymentUnit(DeploymentUnit.builder()
                        .artifactRepositoryUrl("test")
                        .type(DeploymentUnitType.DOCKER_IMAGE)
                        .coordinates("test")
                        .build())
                .build();

        return Deployment.builder()
                .externalId(UUID.randomUUID().toString())
                .startedAt(startedAt)
                .startedBy("user")
                .environment(environment)
                .target(deploymentTarget)
                .componentVersion(componentVersion)
                .sequence(DeploymentSequence.NEW)
                .build();
    }

    public static DeploymentTarget createDeploymentTarget(){
        return new DeploymentTarget("cf", "http://localhost/cf", "details");
    }
}
