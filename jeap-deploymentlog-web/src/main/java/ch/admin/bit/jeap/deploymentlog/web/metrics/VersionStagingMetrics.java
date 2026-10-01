package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class VersionStagingMetrics {
    private static final String METRIC_KEY_SEPARATOR = "\u0000";
    private final VersionDeploymentRepository repository;
    private final DeploymentRepository deploymentRepository;
    private boolean initialized;
    private final StagingProperties properties;
    private final StagingEnvironmentResolver resolver;
    private final MeterRegistry registry;
    private final Map<String, Values> values = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initialize() {
        if (!properties.isEnabled()) return;
        deploymentRepository.reconcileTerminalDeploymentMetrics();
        initialized = true;
        refresh();
    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.deployment-refresh-interval:PT30S}",
            scheduler = DeploymentMetricsSchedulingConfiguration.METRICS_TASK_SCHEDULER)
    public synchronized void refresh() {
        if (!initialized || !properties.isEnabled()) return;
        String start = resolver.resolveStartEnvironment().getName();
        String end = resolver.resolveDefaultFinalDeploymentEnvironment().getName();
        Map<String, VersionArrivalMetricValue> arrivals = repository.arrivalMetrics(start, end).stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> String.join(METRIC_KEY_SEPARATOR, value.system(), value.component(), start, end),
                        java.util.function.Function.identity()));
        java.util.Set<String> active = new java.util.HashSet<>();
        repository.metrics(start, end).forEach(value -> {
            String key = String.join(METRIC_KEY_SEPARATOR, value.system(), value.component(), start, end);
            active.add(key);
            Values current = values.computeIfAbsent(key, ignored -> register(value, start, end, arrivals.get(key)));
            current.value.set(value);
        });
        arrivals.values().forEach(value -> {
            String key = String.join(METRIC_KEY_SEPARATOR, value.system(), value.component(), start, end);
            Values current = values.computeIfAbsent(key, ignored -> register(
                    new StagingMetricValue(value.system(), value.component(), 0, 0, null, 0, 0), start, end, value));
            current.arrivals.set(value);
        });
        values.forEach((key, state) -> {
            if (!active.contains(key)) {
                if (!arrivals.containsKey(key) && !state.arrivals.get().system().isEmpty()) {
                    // A rename/merge moved these durable totals to new labels. Stop exporting the old series.
                    state.meters.forEach(registry::remove);
                    values.remove(key);
                } else {
                    state.value.set(new StagingMetricValue("", "", 0, 0, null, 0, 0));
                }
            }
        });
    }

    private Values register(StagingMetricValue labels, String start, String end, VersionArrivalMetricValue arrivals) {
        Values state = new Values();
        state.value.set(labels);
        // Publish the durable baseline immediately: a scrape during registration must not observe a false zero.
        if (arrivals != null) state.arrivals.set(arrivals);
        String[] tags = {"system", labels.system(), "component", labels.component(),
                "start_environment", start, "end_environment", end};
        state.meters.add(FunctionCounter.builder("version_start_arrivals", state, v -> v.arrivals.get().startVersions()).tags(tags).register(registry));
        state.meters.add(FunctionCounter.builder("version_end_arrivals", state, v -> v.arrivals.get().endVersions()).tags(tags).register(registry));
        state.meters.add(Gauge.builder("version_start", state, v -> v.value.get().startVersions()).tags(tags).register(registry));
        state.meters.add(Gauge.builder("version_end", state, v -> v.value.get().endVersions()).tags(tags).register(registry));
        state.meters.add(Gauge.builder("autostaging_enabled", state, Values::autoStagingEnabled).tags(tags).register(registry));
        state.meters.add(Gauge.builder("version_staging_latency_seconds_count", state, v -> v.value.get().latencyCount()).tags(tags).register(registry));
        state.meters.add(Gauge.builder("version_staging_latency_seconds_sum", state, v -> v.value.get().latencySeconds()).tags(tags).register(registry));
        for (int index = 0; index < StagingLatencyBuckets.UPPER_BOUNDS.size(); index++) {
            int bucketIndex = index;
            state.meters.add(Gauge.builder("version_staging_latency_seconds_buckets", state,
                            v -> v.value.get().latencyBucket(bucketIndex))
                    .tags(tags).tag("le", StagingLatencyBuckets.label(index)).register(registry));
        }
        return state;
    }

    private static class Values {
        private final List<Meter> meters = new ArrayList<>();
        private final AtomicReference<VersionArrivalMetricValue> arrivals =
                new AtomicReference<>(new VersionArrivalMetricValue("", "", 0, 0));
        private final AtomicReference<StagingMetricValue> value =
                new AtomicReference<>(new StagingMetricValue("", "", 0, 0, null, 0, 0));

        private double autoStagingEnabled() {
            Boolean enabled = value.get().autoStagingEnabled();
            if (enabled == null) {
                return Double.NaN;
            }
            return Boolean.TRUE.equals(enabled) ? 1 : 0;
        }
    }
}
