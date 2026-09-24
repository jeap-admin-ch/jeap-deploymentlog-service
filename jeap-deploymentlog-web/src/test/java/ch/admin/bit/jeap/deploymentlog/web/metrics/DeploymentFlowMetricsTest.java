package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricValue;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentStartedMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DeploymentFlowMetricsTest {

    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private SimpleMeterRegistry meterRegistry;
    private DeploymentFlowMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new DeploymentFlowMetrics(meterRegistry, deploymentRepository);
    }

    @Test
    void deploymentStartRegistersZeroBaselinesForEveryTerminalResult() {
        metrics.deploymentStarted(new DeploymentStartedMetricEvent(
                "System", "component", "DEV", Set.of(DeploymentType.CODE)));

        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_COUNTER).functionCounters())
                .extracting(counter -> counter.getId().getTag("result"), FunctionCounter::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("success", 0.0),
                        org.assertj.core.groups.Tuple.tuple("failed", 0.0),
                        org.assertj.core.groups.Tuple.tuple("cancelled", 0.0));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer().count()).isZero();
    }

    @Test
    void refreshRegistersStartedDeploymentBaselinesOnEveryReplica() {
        when(deploymentRepository.findStartedDeploymentMetricIdentities()).thenReturn(List.of(
                new DeploymentMetricIdentity("System", "component", "REF", DeploymentType.CODE)));

        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE", "result", "success")
                .functionCounter().count()).isZero();
    }

    @Test
    void refreshNeverLowersAnAlreadyPublishedDeploymentCounter() {
        DeploymentMetricValue current = deploymentMetricValue(
                "System", "component", "REF", DeploymentType.CODE, DeploymentState.SUCCESS, 2);
        DeploymentMetricValue stale = deploymentMetricValue(
                "System", "component", "REF", DeploymentType.CODE, DeploymentState.SUCCESS, 1);
        when(deploymentRepository.findDeploymentMetricValues())
                .thenReturn(List.of(current))
                .thenReturn(List.of(stale));

        metrics.refreshDeploymentMetrics();
        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE", "result", "success")
                .functionCounter().count()).isEqualTo(2);
    }

    @Test
    void reconciliationBackfillsRollingUpgradeEventsWithoutDuplicatingTheScheduledRefresh() {
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "REF", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 1)));

        metrics.reconcileDeploymentMetrics();

        verify(deploymentRepository).reconcileTerminalDeploymentMetrics();
        verify(deploymentRepository, never()).findDeploymentMetricValues();

        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE", "result", "success")
                .functionCounter().count()).isEqualTo(1);
    }

    @Test
    void ignoresNonTerminalPersistentMetricValueWithoutBlockingValidValues() {
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "DEV", DeploymentType.CODE,
                        DeploymentState.STARTED, 1),
                deploymentMetricValue("System", "component", "REF", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 2)));

        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE", "result", "success")
                .functionCounter().count()).isEqualTo(2);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("environment", "DEV").functionCounters()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DeploymentType.class)
    void recordsTerminalDeploymentCounterAndDurationWithExactDeploymentTypeLabels(DeploymentType deploymentType) {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "Turnus", "turnus-scs", "PROD",
                Set.of(deploymentType), DeploymentState.SUCCESS, startedAt, startedAt.plusSeconds(75)));
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("Turnus", "turnus-scs", "PROD", deploymentType,
                        DeploymentState.SUCCESS, 1)));
        metrics.refreshDeploymentMetrics();

        var counter = meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "Turnus", "component", "turnus-scs", "environment", "PROD", "result", "success",
                        "deployment_type", deploymentType.name())
                .functionCounter();
        assertThat(counter.count()).isEqualTo(1);
        assertTags(counter, Map.of(
                "system", "Turnus",
                "component", "turnus-scs",
                "environment", "PROD",
                "result", "success",
                "deployment_type", deploymentType.name()));

        Timer duration = meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION)
                .tags("system", "Turnus", "component", "turnus-scs", "environment", "PROD",
                        "deployment_type", deploymentType.name()).timer();
        assertThat(duration.count()).isEqualTo(1);
        assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(75);
        assertTags(duration, Map.of(
                "system", "Turnus",
                "component", "turnus-scs",
                "environment", "PROD",
                "deployment_type", deploymentType.name()));
    }

    @Test
    void recordsOneSeriesPerDeploymentTypeForMultiTypeDeployment() {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "System", "component", "PROD",
                Set.of(DeploymentType.CODE, DeploymentType.INFRASTRUCTURE),
                DeploymentState.SUCCESS, startedAt, startedAt.plusSeconds(75)));
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "PROD", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 1),
                deploymentMetricValue("System", "component", "PROD", DeploymentType.INFRASTRUCTURE,
                        DeploymentState.SUCCESS, 1)));
        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("result", "success").functionCounters())
                .extracting(counter -> counter.getId().getTag(DeploymentFlowMetrics.DEPLOYMENT_TYPE),
                        FunctionCounter::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CODE", 1.0),
                        org.assertj.core.groups.Tuple.tuple("INFRASTRUCTURE", 1.0));
        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timers())
                .extracting(timer -> timer.getId().getTag(DeploymentFlowMetrics.DEPLOYMENT_TYPE),
                        Timer::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CODE", 1L),
                        org.assertj.core.groups.Tuple.tuple("INFRASTRUCTURE", 1L));
    }

    @Test
    void recordsFailedDeploymentButSkipsInvalidDuration() {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "System", "component", "DEV",
                Set.of(DeploymentType.CONFIG), DeploymentState.FAILURE, startedAt, startedAt.minusSeconds(1)));
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "DEV", DeploymentType.CONFIG,
                        DeploymentState.FAILURE, 1)));
        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("result", "failed").functionCounter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer().count()).isZero();
    }

    @Test
    void recordsCancelledDeploymentCounterAndDuration() {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "System", "component", "DEV",
                Set.of(DeploymentType.INFRASTRUCTURE), DeploymentState.CANCELLED, startedAt, startedAt.plusSeconds(30)));
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "DEV", DeploymentType.INFRASTRUCTURE,
                        DeploymentState.CANCELLED, 1)));
        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("result", "cancelled").functionCounter().count()).isEqualTo(1);
        Timer duration = meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer();
        assertThat(duration.count()).isEqualTo(1);
        assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(30);
    }

    @Test
    void skipsDeploymentDurationWhenATimestampIsMissing() {
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "System", "component", "DEV",
                Set.of(DeploymentType.CODE), DeploymentState.SUCCESS, null,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00")));
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "DEV", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 1)));
        metrics.refreshDeploymentMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("result", "success").functionCounter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer().count()).isZero();
    }

    @Test
    void startupRegistersHistoricalDeploymentBaselines() {
        when(deploymentRepository.findDeploymentMetricIdentities()).thenReturn(List.of(
                new DeploymentMetricIdentity("System", "component", "REF", DeploymentType.CODE)));

        metrics.initializeMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE", "result", "success")
                .functionCounter().count()).isZero();
    }

    @Test
    void retainedDeploymentMetricRestoresTheCompleteMeterFamily() {
        when(deploymentRepository.findDeploymentMetricIdentities()).thenReturn(List.of());
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "REF", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 3)));

        metrics.initializeMetrics();

        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_COUNTER).functionCounters())
                .extracting(counter -> counter.getId().getTag("result"), FunctionCounter::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("success", 3.0),
                        org.assertj.core.groups.Tuple.tuple("failed", 0.0),
                        org.assertj.core.groups.Tuple.tuple("cancelled", 0.0));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE")
                .timer().count()).isZero();
    }

    @Test
    void missingDeploymentEndIsLoggedWithoutRecordingDuration(CapturedOutput output) {
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "missing-end", "System", "component", "DEV", Set.of(DeploymentType.CODE),
                DeploymentState.FAILURE,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00"), null));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER).functionCounter().count()).isZero();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer().count()).isZero();
        assertThat(output).contains("missing-end", "startedAt or endedAt is missing");
    }

    @Test
    void nonTerminalMetricStatesDoNotCreateDeploymentMeters() {
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "ignored", "System", "component", "DEV", Set.of(DeploymentType.CODE),
                DeploymentState.STARTED, null, null));

        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void deploymentWithoutKnownTypeDoesNotCreateAnUntypedSeries() {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");

        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "unclassified", "System", "component", "DEV", Set.of(),
                DeploymentState.SUCCESS, startedAt, startedAt.plusSeconds(1)));

        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    private DeploymentMetricValue deploymentMetricValue(String system,
                                                        String component,
                                                        String environment,
                                                        DeploymentType deploymentType,
                                                        DeploymentState state,
                                                        long value) {
        return new DeploymentMetricValue(system, component, environment, deploymentType, state, value);
    }

    private static void assertTags(io.micrometer.core.instrument.Meter meter, Map<String, String> expectedTags) {
        assertThat(meter.getId().getTags())
                .extracting(io.micrometer.core.instrument.Tag::getKey, io.micrometer.core.instrument.Tag::getValue)
                .containsExactlyInAnyOrderElementsOf(expectedTags.entrySet().stream()
                        .map(entry -> org.assertj.core.groups.Tuple.tuple(entry.getKey(), entry.getValue()))
                        .toList());
    }
}
