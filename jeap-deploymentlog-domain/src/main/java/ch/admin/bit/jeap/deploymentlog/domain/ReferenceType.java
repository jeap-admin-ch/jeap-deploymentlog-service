package ch.admin.bit.jeap.deploymentlog.domain;

public enum ReferenceType {

    BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION("Build job link by Git URL + '@' + version");

    private final String label;

    ReferenceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
