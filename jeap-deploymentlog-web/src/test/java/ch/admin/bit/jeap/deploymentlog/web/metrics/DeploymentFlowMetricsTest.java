package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentMetricValue;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentStartedMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.FlowOpenMetricsChangedEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowMetricIdentity;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.OpenFlowMetricValue;
import ch.admin.bit.jeap.deploymentlog.domain.OpenFlowMetricIdentity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DeploymentFlowMetricsTest {

    private final FlowRepository flowRepository = mock(FlowRepository.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private SimpleMeterRegistry meterRegistry;
    private DeploymentFlowMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new DeploymentFlowMetrics(meterRegistry, deploymentRepository, flowRepository);
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
    void recordsClosedFlowCounterAndDurationInSeconds() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(FlowType.NEW, FlowState.CLOSED, bornAt, bornAt.plusMinutes(12)));

        var counter = meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tags("system", "System", "component", "component", "type", "new", "state", "closed",
                        "deployment_type", "CODE")
                .counter();
        assertThat(counter.count()).isEqualTo(1);
        assertTags(counter, Map.of(
                "system", "System",
                "component", "component",
                "type", "new",
                "state", "closed",
                "deployment_type", "CODE"));
        Timer duration = meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION)
                .tags("system", "System", "component", "component", "type", "new", "deployment_type", "CODE").timer();
        assertThat(duration.count()).isEqualTo(1);
        assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(12 * 60);
        assertTags(duration, Map.of(
                "system", "System",
                "component", "component",
                "type", "new",
                "deployment_type", "CODE"));
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
    }

    @Test
    void exportsFlowDurationWithTheDocumentedPrometheusNameAndUnit() {
        PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        DeploymentFlowMetrics prometheusMetrics = new DeploymentFlowMetrics(
                prometheusMeterRegistry, deploymentRepository, flowRepository);
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");

        prometheusMetrics.flowReachedTerminalState(
                flowEvent(FlowType.NEW, FlowState.CLOSED, bornAt, bornAt.plusMinutes(12)));

        assertThat(prometheusMeterRegistry.scrape())
                .contains("flow_duration_seconds_count")
                .contains("flow_duration_seconds_sum{component=\"component\",deployment_type=\"CODE\",system=\"System\",type=\"new\"} 720.0")
                .doesNotContain("flow_duration_minutes")
                .doesNotContain("flow_duration_seconds_seconds");
    }

    @Test
    void recordsRollbackRecoveryOnlyForSuccessfullyClosedRollback() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(
                FlowType.ROLLBACK, FlowState.CLOSED, bornAt, bornAt.plusSeconds(90)));

        Timer recovery = meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                .tags("system", "System", "component", "component", "environment", "PROD",
                        "deployment_type", "CODE").timer();
        assertThat(recovery.count()).isEqualTo(1);
        assertThat(recovery.totalTime(TimeUnit.SECONDS)).isEqualTo(90);
        assertTags(recovery, Map.of(
                "system", "System",
                "component", "component",
                "environment", "PROD",
                "deployment_type", "CODE"));
    }

    @Test
    void abortedFlowOnlyIncrementsTerminalCounter() {
        metrics.flowReachedTerminalState(flowEvent(FlowType.AD_HOC, FlowState.ABORTED,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00"), null));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tags("type", "ad_hoc", "state", "aborted", "deployment_type", "CODE").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION).timer().count()).isZero();
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
    }

    @Test
    void rebuildsOpenFlowGaugesFromPersistentStateAndResetsClosedSeries() {
        when(flowRepository.findOpenFlowsForMetrics())
                .thenReturn(List.of(
                        new OpenFlowMetricIdentity(UUID.randomUUID(), "System", "component", "PROD", FlowType.RETRY),
                        new OpenFlowMetricIdentity(UUID.randomUUID(), "System", "component", "PROD", FlowType.RETRY)))
                .thenReturn(List.of());

        metrics.refreshOpenFlowGauges();
        var gauge = meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "retry")
                .gauge();
        assertThat(gauge.value()).isEqualTo(2);
        assertTags(gauge, Map.of(
                "system", "System",
                "component", "component",
                "type", "retry",
                "deployment_type", "CODE"));

        metrics.refreshOpenFlowGauges();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "retry")
                .gauge().value()).isZero();
    }

    @Test
    void registersZeroGaugeForKnownCombinationDuringStartupRefresh() {
        when(deploymentRepository.findDeploymentMetricIdentities()).thenReturn(List.of());
        when(flowRepository.findFlowMetricIdentities()).thenReturn(List.of());
        when(flowRepository.countOpenFlowsBySystemComponentAndType())
                .thenReturn(List.of(new OpenFlowMetricValue("System", "component", FlowType.ROLLBACK, 0)));
        when(flowRepository.findOpenFlowsForMetrics()).thenReturn(List.of());

        when(deploymentRepository.findStartedDeploymentMetricIdentities()).thenReturn(List.of());
        metrics.initializeMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "rollback")
                .gauge().value()).isZero();
    }

    @Test
    void startupRegistersHistoricalDeploymentAndFlowBaselines() {
        when(deploymentRepository.findDeploymentMetricIdentities()).thenReturn(List.of(
                new DeploymentMetricIdentity("System", "component", "REF", DeploymentType.CODE)));
        when(flowRepository.findFlowMetricIdentities()).thenReturn(List.of(
                new FlowMetricIdentity("System", "component", "PROD", FlowType.ROLLBACK)));
        when(flowRepository.countOpenFlowsBySystemComponentAndType()).thenReturn(List.of());
        when(flowRepository.findOpenFlowsForMetrics()).thenReturn(List.of());

        metrics.initializeMetrics();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "System", "component", "component", "environment", "REF",
                        "deployment_type", "CODE", "result", "success")
                .functionCounter().count()).isZero();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tags("system", "System", "component", "component", "type", "rollback",
                        "deployment_type", "CODE", "state", "closed")
                .counter().count()).isZero();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                .tags("system", "System", "component", "component", "environment", "PROD",
                        "deployment_type", "CODE")
                .timer().count()).isZero();
    }

    @Test
    void retainedDeploymentMetricRestoresTheCompleteMeterFamily() {
        when(deploymentRepository.findDeploymentMetricIdentities()).thenReturn(List.of());
        when(deploymentRepository.findDeploymentMetricValues()).thenReturn(List.of(
                deploymentMetricValue("System", "component", "REF", DeploymentType.CODE,
                        DeploymentState.SUCCESS, 3)));
        when(flowRepository.findFlowMetricIdentities()).thenReturn(List.of());
        when(flowRepository.countOpenFlowsBySystemComponentAndType()).thenReturn(List.of());
        when(flowRepository.findOpenFlowsForMetrics()).thenReturn(List.of());

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
    void appliesLocalFlowChangesIncrementallyWithoutQueryingHistory() {
        UUID firstFlowId = UUID.randomUUID();
        UUID secondFlowId = UUID.randomUUID();
        FlowOpenMetricsChangedEvent opened =
                new FlowOpenMetricsChangedEvent(firstFlowId, "System", "component", "PROD", FlowType.NEW, true);
        FlowOpenMetricsChangedEvent secondOpened =
                new FlowOpenMetricsChangedEvent(secondFlowId, "System", "component", "PROD", FlowType.NEW, true);
        FlowOpenMetricsChangedEvent closed =
                new FlowOpenMetricsChangedEvent(firstFlowId, "System", "component", "PROD", FlowType.NEW, false);

        metrics.openFlowsChanged(opened);
        metrics.openFlowsChanged(secondOpened);
        metrics.openFlowsChanged(closed);

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isEqualTo(1);
        verifyNoInteractions(flowRepository);
    }

    @Test
    void openedRollbackRegistersAllTerminalMeterBaselines() {
        metrics.openFlowsChanged(new FlowOpenMetricsChangedEvent(
                UUID.randomUUID(), "System", "component", "PROD", FlowType.ROLLBACK, true));

        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_COUNTER).counters())
                .extracting(counter -> counter.getId().getTag("state"), Counter::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("closed", 0.0),
                        org.assertj.core.groups.Tuple.tuple("aborted", 0.0));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION).timer().count()).isZero();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                .tags("system", "System", "component", "component", "environment", "PROD",
                        "deployment_type", "CODE")
                .timer().count()).isZero();
    }

    @Test
    void reconciliationRegistersRollbackRecoveryBaseline() {
        when(flowRepository.findOpenFlowsForMetrics()).thenReturn(List.of(
                new OpenFlowMetricIdentity(
                        UUID.randomUUID(), "System", "component", "PROD", FlowType.ROLLBACK)));

        metrics.refreshOpenFlowGauges();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                .tags("system", "System", "component", "component", "environment", "PROD",
                        "deployment_type", "CODE")
                .timer().count()).isZero();
    }

    @Test
    void doesNotOverwriteLocalDeltaWithStaleReconciliationResult() {
        UUID flowId = UUID.randomUUID();
        FlowOpenMetricsChangedEvent opened =
                new FlowOpenMetricsChangedEvent(flowId, "System", "component", "PROD", FlowType.NEW, true);
        when(flowRepository.findOpenFlowsForMetrics()).thenAnswer(invocation -> {
            metrics.openFlowsChanged(opened);
            return List.of();
        });

        metrics.refreshOpenFlowGauges();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isEqualTo(1);
    }

    @Test
    void doesNotDoubleApplyEventsAlreadyIncludedInReconciliation() {
        UUID flowId = UUID.randomUUID();
        OpenFlowMetricIdentity openFlow =
                new OpenFlowMetricIdentity(flowId, "System", "component", "PROD", FlowType.NEW);
        when(flowRepository.findOpenFlowsForMetrics())
                .thenReturn(List.of(openFlow))
                .thenReturn(List.of());

        metrics.refreshOpenFlowGauges();
        metrics.openFlowsChanged(
                new FlowOpenMetricsChangedEvent(flowId, "System", "component", "PROD", FlowType.NEW, true));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isEqualTo(1);

        metrics.refreshOpenFlowGauges();
        metrics.openFlowsChanged(
                new FlowOpenMetricsChangedEvent(flowId, "System", "component", "PROD", FlowType.NEW, false));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isZero();
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

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1})
    void invalidClosedRollbackDurationIsLoggedAndOmitted(Integer seconds, CapturedOutput output) {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        FlowTerminalMetricEvent event = flowEvent(FlowType.ROLLBACK, FlowState.CLOSED,
                bornAt, seconds == null ? null : bornAt.plusSeconds(seconds));

        metrics.flowReachedTerminalState(event);

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tag("state", "closed").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION).timer().count()).isZero();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer().count()).isZero();
        assertThat(output).contains("Not recording flow duration for " + event.flowId());
    }

    @ParameterizedTest
    @EnumSource(value = FlowState.class, names = {"OPEN", "ABORTED"})
    void unsuccessfulRollbacksNeverRecordDuration(FlowState state) {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(FlowType.ROLLBACK, state, bornAt, bornAt.plusMinutes(1)));

        if (state == FlowState.ABORTED) {
            assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION).timer().count()).isZero();
        } else {
            assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_DURATION).timer()).isNull();
        }
        if (state == FlowState.OPEN) {
            assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
            assertThat(meterRegistry.getMeters()).isEmpty();
        } else {
            assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer().count()).isZero();
        }
    }

    @Test
    void missingFlowBirthIsLoggedWithoutRecordingDuration(CapturedOutput output) {
        FlowTerminalMetricEvent event = flowEvent(FlowType.NEW, FlowState.CLOSED, null,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00"));
        metrics.flowReachedTerminalState(event);

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION).timer().count()).isZero();
        assertThat(output).contains(event.flowId().toString(), "startedAt or endedAt is missing");
    }

    @ParameterizedTest
    @EnumSource(FlowType.class)
    void allClosedFlowTypesRecordZeroDurationAndNormalizeTypeLabel(FlowType type) {
        ZonedDateTime time = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(type, FlowState.CLOSED, time, time));

        var timer = meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION)
                .tag("type", type.name().toLowerCase(java.util.Locale.ROOT)).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isZero();
        if (type != FlowType.ROLLBACK) {
            assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
        }
    }

    @Test
    void failedReconciliationRetainsLastGaugeAndNextRefreshRecovers() {
        OpenFlowMetricIdentity flow = new OpenFlowMetricIdentity(
                UUID.randomUUID(), "System", "component", "PROD", FlowType.NEW);
        when(flowRepository.findOpenFlowsForMetrics())
                .thenReturn(List.of(flow))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"))
                .thenReturn(List.of());

        metrics.refreshOpenFlowGaugesOnSchedule();
        assertThatThrownBy(metrics::refreshOpenFlowGaugesOnSchedule)
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN).gauge().value()).isEqualTo(1);

        metrics.refreshOpenFlowGaugesOnSchedule();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN).gauge().value()).isZero();
    }

    private FlowTerminalMetricEvent flowEvent(FlowType type, FlowState state,
                                               ZonedDateTime bornAt, ZonedDateTime endedAt) {
        return new FlowTerminalMetricEvent(
                UUID.randomUUID(), "System", "component", "PROD", type, state, bornAt, endedAt);
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
