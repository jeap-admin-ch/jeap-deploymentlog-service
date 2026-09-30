package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VersionStagingMetricsTest {
    @Test void readsDeploymentTotalsAcrossRegistryRestartWithoutFlowTypeOrStateLabels() {
        var repository = mock(VersionDeploymentRepository.class);
        var resolver = mock(StagingEnvironmentResolver.class);
        when(resolver.resolveStartEnvironment()).thenReturn(new Environment("REF"));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));
        when(repository.metrics("REF", "PROD")).thenReturn(List.of(
                new StagingMetricValue("SYS", "service", 5, 3, true, 3, 180)));
        for (int restart = 0; restart < 2; restart++) {
            var registry = new SimpleMeterRegistry();
            try {
                var metrics = new VersionStagingMetrics(repository, mock(DeploymentRepository.class), new StagingProperties(), resolver, registry);
                metrics.initialize(); metrics.refresh();
                assertThat(registry.get("version_start").gauge().value()).isEqualTo(5);
                assertThat(registry.get("version_end").gauge().value()).isEqualTo(3);
                assertThat(registry.get("autostaging_enabled").gauge().value()).isEqualTo(1);
                var latency = registry.get("version_staging_latency_seconds_sum").gauge();
                assertThat(registry.get("version_staging_latency_seconds_count").gauge().value()).isEqualTo(3);
                assertThat(latency.value()).isEqualTo(180);
                assertThat(latency.getId().getTag("type")).isNull();
                assertThat(latency.getId().getTag("state")).isNull();
                assertThat(latency.getId().getTag("version")).isNull();
            } finally {
                registry.close();
            }
        }
    }
    @Test void reflectsRetentionAndClearsTotalsWhenAllDeploymentsAreDeleted() {
        var repository = mock(VersionDeploymentRepository.class);
        var resolver = mock(StagingEnvironmentResolver.class);
        when(resolver.resolveStartEnvironment()).thenReturn(new Environment("REF"));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));
        when(repository.metrics("REF", "PROD")).thenReturn(
                List.of(new StagingMetricValue("SYS", "service", 5, 3, true, 3, 180)),
                List.of(new StagingMetricValue("SYS", "service", 2, 1, false, 1, 60)), List.of());
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new VersionStagingMetrics(repository, mock(DeploymentRepository.class), new StagingProperties(), resolver, registry);
            metrics.initialize();
            metrics.refresh();
            assertThat(registry.get("version_start").gauge().value()).isEqualTo(2);
            assertThat(registry.get("version_staging_latency_seconds_sum").gauge().value()).isEqualTo(60);
            assertThat(registry.get("autostaging_enabled").gauge().value()).isZero();
            metrics.refresh();
            assertThat(registry.get("autostaging_enabled").gauge().value()).isNaN();
            registry.getMeters().stream().filter(meter -> meter instanceof io.micrometer.core.instrument.Gauge && !meter.getId().getName().equals("autostaging_enabled")).forEach(meter -> assertThat(((io.micrometer.core.instrument.Gauge) meter).value()).isZero());
        } finally {
            registry.close();
        }
    }
    @Test void arrivalCountersSurviveRetentionAndRegistryRestart() {
        var repository = mock(VersionDeploymentRepository.class);
        var deploymentRepository = mock(DeploymentRepository.class);
        var resolver = mock(StagingEnvironmentResolver.class);
        when(resolver.resolveStartEnvironment()).thenReturn(new Environment("REF"));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));
        when(repository.metrics("REF", "PROD")).thenReturn(List.of());
        when(repository.arrivalMetrics("REF", "PROD")).thenReturn(List.of(
                new VersionArrivalMetricValue("SYS", "service", 7, 5)));
        for (int restart = 0; restart < 2; restart++) {
            var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            var initialCounts = new java.util.ArrayList<Double>();
            registry.config().onMeterAdded(meter -> {
                if (meter instanceof io.micrometer.core.instrument.FunctionCounter counter) {
                    initialCounts.add(counter.count());
                }
            });
            try {
                var metrics = new VersionStagingMetrics(repository, deploymentRepository,
                        new StagingProperties(), resolver, registry);
                metrics.refresh();
                assertThat(registry.getMeters()).isEmpty();
                metrics.initialize();
                metrics.refresh();
                assertThat(initialCounts).containsExactly(7.0, 5.0);
                assertThat(registry.get("version_start_arrivals").functionCounter().count()).isEqualTo(7);
                assertThat(registry.get("version_end_arrivals").functionCounter().count()).isEqualTo(5);
                assertThat(registry.scrape()).contains("# TYPE version_start_arrivals_total counter",
                        "# TYPE version_end_arrivals_total counter");
                assertThat(registry.get("version_start").gauge().value()).isZero();
                assertThat(registry.get("autostaging_enabled").gauge().value()).isNaN();
            } finally {
                registry.close();
            }
        }
        verify(deploymentRepository, times(2)).reconcileTerminalDeploymentMetrics();
    }

    @Test void exportsCumulativeLatencyBucketsAndReplacesThemAfterRetention() {
        var repository = mock(VersionDeploymentRepository.class);
        var resolver = mock(StagingEnvironmentResolver.class);
        when(resolver.resolveStartEnvironment()).thenReturn(new Environment("REF"));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));
        List<Long> counts = StagingLatencyBuckets.UPPER_BOUNDS.stream()
                .map(bound -> bound < 3600 ? 1L : 2L).toList();
        when(repository.metrics("REF", "PROD")).thenReturn(
                List.of(new StagingMetricValue("SYS", "service", 2, 2, true, 2, 3600, counts)),
                List.of());
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            var metrics = new VersionStagingMetrics(repository, mock(DeploymentRepository.class),
                    new StagingProperties(), resolver, registry);
            metrics.initialize();
            for (int index = 0; index < counts.size(); index++) {
                assertThat(registry.get("version_staging_latency_seconds_buckets")
                        .tag("le", StagingLatencyBuckets.label(index)).gauge().value()).isEqualTo(counts.get(index).doubleValue());
            }
            assertThat(registry.scrape()).contains(
                    "# TYPE version_staging_latency_seconds_buckets gauge",
                    "version_staging_latency_seconds_buckets{",
                    "le=\"+Inf\"",
                    "version_staging_latency_seconds_sum",
                    "version_staging_latency_seconds_count");
            metrics.refresh();
            registry.find("version_staging_latency_seconds_buckets").gauges()
                    .forEach(bucket -> assertThat(bucket.value()).isZero());
        } finally {
            registry.close();
        }
    }

}
