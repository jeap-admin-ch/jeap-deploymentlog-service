package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;

/** Inclusive upper bounds in seconds for the retained-version latency distribution. */
public final class StagingLatencyBuckets {
    public static final List<Double> UPPER_BOUNDS = List.of(
            0.0, 60.0, 300.0, 900.0, 1800.0, 3600.0, 7200.0, 14400.0,
            28800.0, 43200.0, 86400.0, 172800.0, 259200.0, 432000.0, 604800.0,
            1209600.0, 1814400.0, 2592000.0, 5184000.0, 7776000.0, 15552000.0,
            31536000.0, 63072000.0, 157680000.0, 315360000.0, Double.POSITIVE_INFINITY);

    private StagingLatencyBuckets() {
    }

    public static String label(int index) {
        double bound = UPPER_BOUNDS.get(index);
        return Double.isInfinite(bound) ? "+Inf" : Long.toString((long) bound);
    }
}
