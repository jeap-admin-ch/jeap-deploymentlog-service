package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DeploymentSequence {

    FIRST("Erstes Deployment der Komponente auf die Umgebung"),
    NEW("Deployment einer neuen Version"),
    REPEATED("Wiederholung des Deployments derselben Version"),
    UNDEPLOYED("Löschung der Komponente aus der Umgebung");

    private final String label;

}
