package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
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
    void recordsTerminalMetricsOnlyAfterCommit() {
        Fixture fixture = createFixture();
        transaction().executeWithoutResult(status -> {
            update(fixture, DeploymentState.SUCCESS);
            assertNoTerminalMeters(fixture);
        });

        assertRecordedOnce(fixture, DeploymentState.SUCCESS);
    }

    @Test
    void outerRollbackDiscardsStatusAndMetricEventsAndRetryCountsOnce() {
        Fixture fixture = createFixture();
        transaction().executeWithoutResult(status -> {
            update(fixture, DeploymentState.SUCCESS);
            status.setRollbackOnly();
        });

        assertNoTerminalMeters(fixture);
        transaction().executeWithoutResult(status -> {
            var deployment = deploymentRepository.findByExternalId(fixture.externalId()).orElseThrow();
            assertThat(deployment.getState()).isEqualTo(DeploymentState.STARTED);
        });

        update(fixture, DeploymentState.SUCCESS);
        assertRecordedOnce(fixture, DeploymentState.SUCCESS);
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentState.class, names = {"SUCCESS", "FAILURE", "CANCELLED"})
    void concurrentAndRepeatedStatusUpdatesCountOnce(DeploymentState state) throws Exception {
        Fixture fixture = createFixture();
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
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void actuatorExportsDeploymentMetricsWithExactLabelsAndSeconds() throws Exception {
        Fixture fixture = createFixture();
        update(fixture, DeploymentState.SUCCESS);
        metrics.refreshDeploymentMetrics();

        String scrape = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String system = fixture.system();
        assertThat(scrape.lines().filter(line -> line.contains("system=\"" + system + "\"")).toList())
                .contains(
                        "deployment_counter_total{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",result=\"success\",system=\"" + system + "\"} 1.0",
                        "deployment_duration_seconds_count{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",system=\"" + system + "\"} 1",
                        "deployment_duration_seconds_sum{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",system=\"" + system + "\"} 90.0");
        assertThat(scrape).doesNotContain(fixture.externalId(), "flow_duration_minutes", "flow_duration_seconds_seconds");
    }

    @Test
    void actuatorExportsZeroBaselineBeforeDeploymentCompletes() throws Exception {
        Fixture fixture = createFixture();
        metrics.refreshDeploymentMetrics();

        String scrape = mockMvc.perform(get("/actuator/prometheus")
                        .accept("text/plain"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(scrape)
                .contains("deployment_counter_total{component=\"service\",deployment_type=\"CODE\",environment=\"DEV\",result=\"success\",system=\"" + fixture.system() + "\"} 0.0");
    }

    private void assertNoTerminalMeters(Fixture fixture) {
        assertThat(registry.find(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tag("system", fixture.system()).functionCounters())
                .isNotEmpty().allMatch(counter -> counter.count() == 0);
        assertThat(registry.find(DeploymentFlowMetrics.DEPLOYMENT_DURATION)
                .tag("system", fixture.system()).timers())
                .isNotEmpty().allMatch(timer -> timer.count() == 0);
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
