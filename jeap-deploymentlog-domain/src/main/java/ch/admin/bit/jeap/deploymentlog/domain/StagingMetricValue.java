package ch.admin.bit.jeap.deploymentlog.domain;

public record StagingMetricValue(String system, String component, long startVersions, long endVersions,
                                 Boolean autoStagingEnabled,
                                 long latencyCount, double latencySeconds) {
}
