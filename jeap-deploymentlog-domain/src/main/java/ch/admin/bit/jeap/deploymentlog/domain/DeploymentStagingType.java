package ch.admin.bit.jeap.deploymentlog.domain;

/** Classification of an individual deployment relative to its stage history. */
public enum DeploymentStagingType {
    NEW, RETRY, ROLLBACK, AD_HOC
}
