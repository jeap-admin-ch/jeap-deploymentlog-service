package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;

public record StagingMetricValue(String system, String component, long startVersions, long endVersions,
                                 Boolean autoStagingEnabled,
                                 long latencyCount, double latencySeconds, List<Long> latencyBuckets) {
    public StagingMetricValue {
        latencyBuckets = List.copyOf(latencyBuckets);
    }

    // Preserve the constructor for consumers that only provide totals.
    public StagingMetricValue(String system, String component, long startVersions, long endVersions,
                              Boolean autoStagingEnabled, long latencyCount, double latencySeconds) {
        this(system, component, startVersions, endVersions, autoStagingEnabled, latencyCount, latencySeconds, List.of());
    }

    public double latencyBucket(int index) {
        if (latencyBuckets.isEmpty()) {
            return latencyCount == 0 ? 0 : Double.NaN;
        }
        return latencyBuckets.get(index);
    }
}
