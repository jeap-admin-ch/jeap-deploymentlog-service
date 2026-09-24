package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VersionStagingMetricsTest {
    @Test void readsDeploymentTotalsAcrossRegistryRestartWithoutFlowTypeOrStateLabels() {
        var repository = mock(VersionDeploymentRepository.class);
        var resolver = mock(FlowStageResolver.class);
        when(resolver.resolveStartEnvironment()).thenReturn(new Environment("REF"));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));
        when(repository.metrics("REF", "PROD")).thenReturn(List.of(
                new StagingMetricValue("SYS", "service", 5, 3, true, 3, 180)));
        for (int restart = 0; restart < 2; restart++) {
            var registry = new SimpleMeterRegistry();
            try {
                var metrics = new VersionStagingMetrics(repository, new FlowStageProperties(), resolver, registry);
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
        var resolver = mock(FlowStageResolver.class);
        when(resolver.resolveStartEnvironment()).thenReturn(new Environment("REF"));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));
        when(repository.metrics("REF", "PROD")).thenReturn(
                List.of(new StagingMetricValue("SYS", "service", 5, 3, true, 3, 180)),
                List.of(new StagingMetricValue("SYS", "service", 2, 1, false, 1, 60)), List.of());
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new VersionStagingMetrics(repository, new FlowStageProperties(), resolver, registry);
            metrics.initialize();
            metrics.refresh();
            assertThat(registry.get("version_start").gauge().value()).isEqualTo(2);
            assertThat(registry.get("version_staging_latency_seconds_sum").gauge().value()).isEqualTo(60);
            assertThat(registry.get("autostaging_enabled").gauge().value()).isZero();
            metrics.refresh();
            assertThat(registry.get("autostaging_enabled").gauge().value()).isNaN();
            registry.getMeters().stream().filter(meter -> !meter.getId().getName().equals("autostaging_enabled")).forEach(meter -> assertThat(((io.micrometer.core.instrument.Gauge) meter).value()).isZero());
        } finally {
            registry.close();
        }
    }
}
