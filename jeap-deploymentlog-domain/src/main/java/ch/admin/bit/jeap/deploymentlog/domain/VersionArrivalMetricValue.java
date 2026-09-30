package ch.admin.bit.jeap.deploymentlog.domain;

public record VersionArrivalMetricValue(String system, String component, long startVersions, long endVersions) {
}
