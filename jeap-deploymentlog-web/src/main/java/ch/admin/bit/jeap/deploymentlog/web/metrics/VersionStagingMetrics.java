package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class VersionStagingMetrics {
    private static final String METRIC_KEY_SEPARATOR = "\u0000";
    private final VersionDeploymentRepository repository;
    private final FlowStageProperties properties;
    private final FlowStageResolver resolver;
    private final MeterRegistry registry;
    private final Map<String, Values> values = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${jeap.deploymentlog.metrics.deployment-refresh-interval:PT30S}",
            scheduler = DeploymentMetricsSchedulingConfiguration.METRICS_TASK_SCHEDULER)
    public synchronized void refresh() {
        if (!properties.isEnabled()) return;
        String start = resolver.resolveStartEnvironment().getName();
        String end = resolver.resolveDefaultFinalDeploymentEnvironment().getName();
        java.util.Set<String> active = new java.util.HashSet<>();
        repository.metrics(start, end).forEach(value -> {
            String key = String.join(METRIC_KEY_SEPARATOR, value.system(), value.component(), start, end);
            active.add(key);
            Values current = values.computeIfAbsent(key, ignored -> register(value, start, end));
            current.value.set(value);
        });
        values.forEach((key, state) -> {
            if (!active.contains(key)) state.value.set(new StagingMetricValue("", "", 0, 0, null, 0, 0));
        });
    }

    private Values register(StagingMetricValue labels, String start, String end) {
        Values state = new Values();
        String[] tags = {"system", labels.system(), "component", labels.component(),
                "start_environment", start, "end_environment", end};
        Gauge.builder("version_start", state, v -> v.value.get().startVersions()).tags(tags).register(registry);
        Gauge.builder("version_end", state, v -> v.value.get().endVersions()).tags(tags).register(registry);
        Gauge.builder("autostaging_enabled", state, Values::autoStagingEnabled).tags(tags).register(registry);
        Gauge.builder("version_staging_latency_seconds_count", state, v -> v.value.get().latencyCount()).tags(tags).register(registry);
        Gauge.builder("version_staging_latency_seconds_sum", state, v -> v.value.get().latencySeconds()).tags(tags).register(registry);
        return state;
    }

    private static class Values {
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
