package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Intentionally two ordered phases: destroy the entire Spring context, retain only the database, then start again.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeploymentFlowMetricsRestartTest extends MetricsIntegrationTestBase {
    private static final String DATABASE = "metrics-restart-" + UUID.randomUUID();
    private static Fixture closed;
    private static Fixture open;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:" + DATABASE + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @Test
    @Order(1)
    void beforeRestartRecordsOneClosedAndOneOpenFlow() {
        closed = createFixture(FlowType.ROLLBACK);
        open = createFixture(FlowType.RETRY);
        update(closed, DeploymentState.SUCCESS);

        assertThat(registry.get(DeploymentFlowMetrics.DEPLOYMENT_COUNTER)
                .tags("system", closed.system(), "result", "success").counter().count()).isEqualTo(1);
        assertThat(openFlows(closed)).isZero();
        assertThat(openFlows(open)).isEqualTo(1);
    }

    @Test
    @Order(2)
    void afterRestartRebuildsGaugesAndDoesNotCountPersistedTerminalStateAgain() {
        // Do not call initialize manually: ApplicationReadyEvent must reconstruct these gauges.
        assertThat(closed).isNotNull();
        assertThat(openFlows(closed)).isZero();
        assertThat(openFlows(open)).isEqualTo(1);
        update(closed, DeploymentState.SUCCESS);

        // Historical label combinations are restored as zero baselines, but terminal observations are not replayed.
        for (String name : new String[]{DeploymentFlowMetrics.DEPLOYMENT_COUNTER, DeploymentFlowMetrics.DEPLOYMENT_DURATION,
                DeploymentFlowMetrics.FLOW_COUNTER, DeploymentFlowMetrics.FLOW_DURATION, DeploymentFlowMetrics.FLOW_RECOVERY_DURATION}) {
            assertThat(registry.find(name).tag("system", closed.system()).meters())
                    .isNotEmpty()
                    .allMatch(meter -> switch (meter) {
                        case io.micrometer.core.instrument.Counter counter -> counter.count() == 0;
                        case io.micrometer.core.instrument.Timer timer -> timer.count() == 0;
                        default -> false;
                    });
        }
        update(open, DeploymentState.SUCCESS);
        assertThat(registry.get(DeploymentFlowMetrics.FLOW_COUNTER)
                .tags("system", open.system(), "state", "closed").counter().count()).isEqualTo(1);
        assertThat(openFlows(open)).isZero();
    }
}
