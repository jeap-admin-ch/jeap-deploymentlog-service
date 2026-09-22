package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentFlowMetricsIntegrationTest extends MetricsIntegrationTestBase {

    @Test
    void recordsTerminalMetricsAndChangesGaugeOnlyAfterCommit() {
        Fixture fixture = createFixture(FlowType.ROLLBACK);
        transaction().executeWithoutResult(status -> {
            update(fixture, DeploymentState.SUCCESS);
            assertNoTerminalMeters(fixture);
            assertThat(openFlows(fixture)).isEqualTo(1);
        });

        assertRecordedOnce(fixture, DeploymentState.SUCCESS);
        assertThat(openFlows(fixture)).isZero();
    }

    @Test
    void rollbackDiscardsAllMetricEventsAndRetryCountsOnce() {
        Fixture fixture = createFixture(FlowType.ROLLBACK);
        transaction().executeWithoutResult(status -> {
            update(fixture, DeploymentState.SUCCESS);
            status.setRollbackOnly();
        });

        assertNoTerminalMeters(fixture);
        assertThat(openFlows(fixture)).isEqualTo(1);
        transaction().executeWithoutResult(status -> {
            var deployment = deploymentRepository.findByExternalId(fixture.externalId()).orElseThrow();
            assertThat(deployment.getState()).isEqualTo(DeploymentState.STARTED);
            assertThat(flowRepository.findByDeploymentId(deployment.getId()).orElseThrow().getState()).isEqualTo(FlowState.OPEN);
        });

        update(fixture, DeploymentState.SUCCESS);
        assertRecordedOnce(fixture, DeploymentState.SUCCESS);
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentState.class, names = {"SUCCESS", "FAILURE", "CANCELLED"})
    void concurrentAndRepeatedStatusUpdatesCountOnce(DeploymentState state) throws Exception {
        Fixture fixture = createFixture(FlowType.ROLLBACK);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Runnable request = () -> {
                ready.countDown();
                await(start);
                update(fixture, state);
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            update(fixture, state);

            assertRecordedOnce(fixture, state);
            assertThat(openFlows(fixture)).isEqualTo(state == DeploymentState.SUCCESS ? 0 : 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void actuatorExportsAllSixMetricsWithExactLabelsAndSeconds() throws Exception {
        Fixture fixture = createFixture(FlowType.ROLLBACK);
        update(fixture, DeploymentState.SUCCESS);
        metrics.refreshDeploymentMetrics();

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String system = fixture.system();
        assertThat(scrape.lines().filter(line -> line.contains("system=\"" + system + "\"")).toList())
                .contains(
                        "deployment_counter_total{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",result=\"success\",system=\"" + system + "\"} 1.0",
                        "deployment_duration_seconds_count{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",system=\"" + system + "\"} 1",
                        "deployment_duration_seconds_sum{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",system=\"" + system + "\"} 90.0",
                        "flow_counter_total{component=\"service\",deployment_type=\"CODE\",state=\"closed\",system=\"" + system + "\",type=\"rollback\"} 1.0",
                        "flow_open{component=\"service\",deployment_type=\"CODE\",system=\"" + system + "\",type=\"rollback\"} 0.0",
                        "flow_duration_seconds_count{component=\"service\",deployment_type=\"CODE\",system=\"" + system + "\",type=\"rollback\"} 1",
                        "flow_duration_seconds_sum{component=\"service\",deployment_type=\"CODE\",system=\"" + system + "\",type=\"rollback\"} 90.0",
                        "flow_recovery_duration_seconds_count{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",system=\"" + system + "\"} 1",
                        "flow_recovery_duration_seconds_sum{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",system=\"" + system + "\"} 90.0");
        assertThat(scrape).doesNotContain(fixture.externalId(), "flow_duration_minutes", "flow_duration_seconds_seconds");
    }

    @Test
    void actuatorExportsZeroBaselineBeforeDeploymentCompletes() throws Exception {
        Fixture fixture = createFixture(FlowType.NEW);
        metrics.refreshDeploymentMetrics();

        String scrape = mockMvc.perform(get("/actuator/prometheus")
                        .accept("text/plain"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(scrape)
                .contains("deployment_counter_total{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",result=\"success\",system=\"" + fixture.system() + "\"} 0.0");
    }

    private void assertNoTerminalMeters(Fixture fixture) {
        assertThat(registry.find(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("system", fixture.system()).functionCounters()).allMatch(counter -> counter.count() == 0);
        assertThat(registry.find(DeploymentFlowMetrics.DEPLOYMENT_DURATION)
                .tag("system", fixture.system()).timers()).allMatch(timer -> timer.count() == 0);
        assertThat(registry.find(DeploymentFlowMetrics.FLOW_COUNTER)
                .tag("system", fixture.system()).counters()).allMatch(counter -> counter.count() == 0);
        assertThat(registry.find(DeploymentFlowMetrics.FLOW_DURATION)
                .tag("system", fixture.system()).timers()).allMatch(timer -> timer.count() == 0);
        assertThat(registry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                .tag("system", fixture.system()).timers()).allMatch(timer -> timer.count() == 0);
    }

    private void assertRecordedOnce(Fixture fixture, DeploymentState state) {
        metrics.refreshDeploymentMetrics();
        String result = switch (state) {
            case SUCCESS -> "success";
            case FAILURE -> "failed";
            case CANCELLED -> "cancelled";
            default -> throw new IllegalArgumentException("Expected a terminal deployment state");
        };
        assertThat(registry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER).tags("system", fixture.system(),
                "result", result, "deployment_type", "CODE").functionCounter().count()).isEqualTo(1);
        var duration = registry.get(DeploymentFlowMetrics.DEPLOYMENT_DURATION)
                .tags("system", fixture.system(), "deployment_type", "CODE").timer();
        assertThat(duration.count()).isEqualTo(1);
        assertThat(duration.totalTime(TimeUnit.SECONDS)).isEqualTo(90);
        if (state == DeploymentState.SUCCESS) {
            assertThat(registry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                    .tags("system", fixture.system(), "deployment_type", "CODE", "state", "closed")
                    .counter().count()).isEqualTo(1);
            for (String name : new String[]{DeploymentFlowMetrics.FLOW_DURATION, DeploymentFlowMetrics.FLOW_RECOVERY_DURATION}) {
                var timer = registry.get(name)
                        .tags("system", fixture.system(), "deployment_type", "CODE").timer();
                assertThat(timer.count()).isEqualTo(1);
                assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(90);
            }
        } else {
            assertThat(registry.find(DeploymentFlowMetrics.FLOW_COUNTER)
                    .tag("system", fixture.system()).counters()).allMatch(counter -> counter.count() == 0);
            assertThat(registry.find(DeploymentFlowMetrics.FLOW_DURATION)
                    .tag("system", fixture.system()).timers()).allMatch(timer -> timer.count() == 0);
            assertThat(registry.find(DeploymentFlowMetrics.FLOW_RECOVERY_DURATION)
                    .tag("system", fixture.system()).timers()).allMatch(timer -> timer.count() == 0);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent status updates");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
