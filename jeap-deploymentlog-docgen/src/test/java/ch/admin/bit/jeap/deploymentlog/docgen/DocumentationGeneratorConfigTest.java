package ch.admin.bit.jeap.deploymentlog.docgen;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentationGeneratorConfigTest {

    @Test
    void registersTimedBaselinesWithTheTagsUsedByTimedAspect() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new DocumentationGeneratorConfig().documentationMetricBaselines(registry)
                .onApplicationEvent(mock(ApplicationReadyEvent.class));

        assertTimedBaseline(registry, "deploymentlog_generate_deployment_page", "generateDeploymentPages");
        assertTimedBaseline(registry, "update_deployment_history_pages", "updateDeploymentHistoryPages");
    }

    private void assertTimedBaseline(SimpleMeterRegistry registry, String name, String method) {
        assertThat(registry.get(name)
                .tags("class", DocumentationGenerator.class.getName(), "method", method, "exception", "none")
                .timer().count()).isZero();
    }
}
