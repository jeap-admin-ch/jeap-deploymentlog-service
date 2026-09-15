package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowOpenMetricsChangedEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowTerminalMetricEvent;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.OpenFlowMetricValue;
import ch.admin.bit.jeap.deploymentlog.domain.OpenFlowMetricIdentity;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DeploymentFlowMetricsTest {

    private final FlowRepository flowRepository = mock(FlowRepository.class);
    private SimpleMeterRegistry meterRegistry;
    private DeploymentFlowMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new DeploymentFlowMetrics(meterRegistry, flowRepository);
    }

    @Test
    void recordsTerminalDeploymentCounterAndDurationExactlyWithDocumentedLabels() {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "Turnus", "turnus-scs", "PROD",
                DeploymentState.SUCCESS, startedAt, startedAt.plusSeconds(75)));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", "Turnus", "component", "turnus-scs", "environment", "PROD", "result", "success")
                .counter().count()).isEqualTo(1);
        Timer duration = meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION)
                .tags("system", "Turnus", "component", "turnus-scs", "environment", "PROD").timer();
        assertThat(duration.count()).isEqualTo(1);
        assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(75);
    }

    @Test
    void recordsFailedDeploymentButSkipsInvalidDuration() {
        ZonedDateTime startedAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "System", "component", "DEV",
                DeploymentState.FAILURE, startedAt, startedAt.minusSeconds(1)));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("result", "failed").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer()).isNull();
    }

    @Test
    void skipsDeploymentDurationWhenATimestampIsMissing() {
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "external-id", "System", "component", "DEV",
                DeploymentState.SUCCESS, null, ZonedDateTime.parse("2026-09-14T10:00:00+02:00")));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer()).isNull();
    }

    @Test
    void recordsClosedFlowCounterAndDurationInSeconds() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(FlowType.NEW, FlowState.CLOSED, bornAt, bornAt.plusMinutes(12)));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tags("system", "System", "component", "component", "type", "new", "state", "closed")
                .counter().count()).isEqualTo(1);
        Timer duration = meterRegistry.get(DeploymentFlowMetrics.FLOW_DURATION)
                .tags("system", "System", "component", "component", "type", "new").timer();
        assertThat(duration.count()).isEqualTo(1);
        assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(12 * 60);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
    }

    @Test
    void exportsFlowDurationWithTheDocumentedPrometheusNameAndUnit() {
        PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        DeploymentFlowMetrics prometheusMetrics = new DeploymentFlowMetrics(prometheusMeterRegistry, flowRepository);
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");

        prometheusMetrics.flowReachedTerminalState(
                flowEvent(FlowType.NEW, FlowState.CLOSED, bornAt, bornAt.plusMinutes(12)));

        assertThat(prometheusMeterRegistry.scrape())
                .contains("flow_duration_seconds_count")
                .contains("flow_duration_seconds_sum{component=\"component\",system=\"System\",type=\"new\"} 720.0")
                .doesNotContain("flow_duration_minutes")
                .doesNotContain("flow_duration_seconds_seconds");
    }

    @Test
    void recordsRollbackRecoveryOnlyForSuccessfullyClosedRollback() {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(
                FlowType.ROLLBACK, FlowState.CLOSED, bornAt, bornAt.plusSeconds(90)));

        Timer recovery = meterRegistry.get(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                .tags("system", "System", "component", "component", "environment", "PROD").timer();
        assertThat(recovery.count()).isEqualTo(1);
        assertThat(recovery.totalTime(TimeUnit.SECONDS)).isEqualTo(90);
    }

    @Test
    void abortedFlowOnlyIncrementsTerminalCounter() {
        metrics.flowReachedTerminalState(flowEvent(FlowType.AD_HOC, FlowState.ABORTED,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00"), null));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tags("type", "ad_hoc", "state", "aborted").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_DURATION).timer()).isNull();
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
    }

    @Test
    void rebuildsOpenFlowGaugesFromPersistentStateAndResetsClosedSeries() {
        when(flowRepository.findOpenFlowsForMetrics())
                .thenReturn(List.of(
                        new OpenFlowMetricIdentity(UUID.randomUUID(), "System", "component", FlowType.RETRY),
                        new OpenFlowMetricIdentity(UUID.randomUUID(), "System", "component", FlowType.RETRY)))
                .thenReturn(List.of());

        metrics.refreshOpenFlowGauges();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "retry")
                .gauge().value()).isEqualTo(2);

        metrics.refreshOpenFlowGauges();
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "retry")
                .gauge().value()).isZero();
    }

    @Test
    void registersZeroGaugeForKnownCombinationDuringStartupRefresh() {
        when(flowRepository.countOpenFlowsBySystemComponentAndType())
                .thenReturn(List.of(new OpenFlowMetricValue("System", "component", FlowType.ROLLBACK, 0)));
        when(flowRepository.findOpenFlowsForMetrics()).thenReturn(List.of());

        metrics.initializeOpenFlowGauges();

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "rollback")
                .gauge().value()).isZero();
    }

    @Test
    void appliesLocalFlowChangesIncrementallyWithoutQueryingHistory() {
        UUID firstFlowId = UUID.randomUUID();
        UUID secondFlowId = UUID.randomUUID();
        FlowOpenMetricsChangedEvent opened =
                new FlowOpenMetricsChangedEvent(firstFlowId, "System", "component", FlowType.NEW, true);
        FlowOpenMetricsChangedEvent secondOpened =
                new FlowOpenMetricsChangedEvent(secondFlowId, "System", "component", FlowType.NEW, true);
        FlowOpenMetricsChangedEvent closed =
                new FlowOpenMetricsChangedEvent(firstFlowId, "System", "component", FlowType.NEW, false);

        metrics.openFlowsChanged(opened);
        metrics.openFlowsChanged(secondOpened);
        metrics.openFlowsChanged(closed);

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isEqualTo(1);
        verifyNoInteractions(flowRepository);
    }

    @Test
    void doesNotOverwriteLocalDeltaWithStaleReconciliationResult() {
        UUID flowId = UUID.randomUUID();
        FlowOpenMetricsChangedEvent opened =
                new FlowOpenMetricsChangedEvent(flowId, "System", "component", FlowType.NEW, true);
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
                new OpenFlowMetricIdentity(flowId, "System", "component", FlowType.NEW);
        when(flowRepository.findOpenFlowsForMetrics())
                .thenReturn(List.of(openFlow))
                .thenReturn(List.of());

        metrics.refreshOpenFlowGauges();
        metrics.openFlowsChanged(
                new FlowOpenMetricsChangedEvent(flowId, "System", "component", FlowType.NEW, true));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isEqualTo(1);

        metrics.refreshOpenFlowGauges();
        metrics.openFlowsChanged(
                new FlowOpenMetricsChangedEvent(flowId, "System", "component", FlowType.NEW, false));
        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_OPEN)
                .tags("system", "System", "component", "component", "type", "new")
                .gauge().value()).isZero();
    }

    @Test
    void missingDeploymentEndIsLoggedWithoutRecordingDuration(CapturedOutput output) {
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "missing-end", "System", "component", "DEV", DeploymentState.FAILURE,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00"), null));

        assertThat(meterRegistry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.DEPLOYMENT_DURATION).timer()).isNull();
        assertThat(output).contains("missing-end", "startedAt or endedAt is missing");
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentState.class, names = {"STARTED", "CANCELLED"})
    void nonTerminalMetricStatesDoNotCreateDeploymentMeters(DeploymentState state) {
        metrics.deploymentReachedTerminalState(new DeploymentTerminalMetricEvent(
                UUID.randomUUID(), "ignored", "System", "component", "DEV", state, null, null));

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

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_DURATION).timer()).isNull();
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
        assertThat(output).contains("Not recording flow duration for " + event.flowId());
    }

    @ParameterizedTest
    @EnumSource(value = FlowState.class, names = {"OPEN", "ABORTED"})
    void unsuccessfulRollbacksNeverRecordDuration(FlowState state) {
        ZonedDateTime bornAt = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");
        metrics.flowReachedTerminalState(flowEvent(FlowType.ROLLBACK, state, bornAt, bornAt.plusMinutes(1)));

        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_DURATION).timer()).isNull();
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION).timer()).isNull();
        if (state == FlowState.OPEN) {
            assertThat(meterRegistry.getMeters()).isEmpty();
        }
    }

    @Test
    void missingFlowBirthIsLoggedWithoutRecordingDuration(CapturedOutput output) {
        FlowTerminalMetricEvent event = flowEvent(FlowType.NEW, FlowState.CLOSED, null,
                ZonedDateTime.parse("2026-09-14T10:00:00+02:00"));
        metrics.flowReachedTerminalState(event);

        assertThat(meterRegistry.get(DeploymentFlowMetrics.FLOW_COUNTER).counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(DeploymentFlowMetrics.FLOW_DURATION).timer()).isNull();
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
        OpenFlowMetricIdentity flow = new OpenFlowMetricIdentity(UUID.randomUUID(), "System", "component", FlowType.NEW);
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
}
