package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.Getter;

public enum DeploymentUnitType {
    DOCKER_IMAGE("Docker Image"),
    MAVEN_JAR("Maven JAR"),
    NPM_PACKAGE("NPM Package"),
    SOURCE_BUILD("Build from source code"),
    GIT_OPS_COMMIT("GitOps Repo Commit");

    @Getter
    private final String label;

    DeploymentUnitType(String label) {
        this.label = label;
    }
}
