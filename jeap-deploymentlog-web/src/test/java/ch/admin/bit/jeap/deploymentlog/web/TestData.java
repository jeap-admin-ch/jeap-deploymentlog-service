package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.UndeploymentCreateDto;

import java.time.ZonedDateTime;
import java.util.Collections;

public abstract class TestData {

    public static UndeploymentCreateDto getUndeploymentCreateDto() {
        UndeploymentCreateDto undeploymentCreateDto = new UndeploymentCreateDto();
        undeploymentCreateDto.setSystemName("testSystem");
        undeploymentCreateDto.setComponentName("testComponent");
        undeploymentCreateDto.setEnvironmentName("ref");
        undeploymentCreateDto.setStartedBy("user1");
        undeploymentCreateDto.setStartedAt(ZonedDateTime.now());
        undeploymentCreateDto.setRemedyChangeId("remedy-1");
        return undeploymentCreateDto;
    }

    public static Deployment getDeployment() {
        Environment environment = new Environment("test");
        DeploymentTarget deploymentTarget = new DeploymentTarget("test", "http://localhost/cf", "details");
        Component component = new Component("test", getSystem());
        ComponentVersion componentVersion = ComponentVersion.builder()
                .versionName("test")
                .taggedAt(ZonedDateTime.now())
                .versionControlUrl("test")
                .component(component)
                .deploymentUnit(getDeploymentUnit())
                .publishedVersion(false)
                .committedAt(ZonedDateTime.now())
                .commitRef("test")
                .build();
        return Deployment.builder().externalId("externalId")
                .startedAt(ZonedDateTime.now())
                .startedBy("user")
                .environment(environment)
                .target(deploymentTarget)
                .componentVersion(componentVersion)
                .links(Collections.emptySet())
                .sequence(DeploymentSequence.NEW)
                .build();
    }

    private static DeploymentUnit getDeploymentUnit() {
        return DeploymentUnit.builder().artifactRepositoryUrl("test").coordinates("test").type(DeploymentUnitType.DOCKER_IMAGE).build();
    }

    private static System getSystem() {
        return new System("test");
    }

}
