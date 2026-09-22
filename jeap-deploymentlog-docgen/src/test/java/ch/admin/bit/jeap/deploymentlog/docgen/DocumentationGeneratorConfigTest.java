package ch.admin.bit.jeap.deploymentlog.docgen;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentationGeneratorConfigTest {

    @Test
    void usesConfiguredDocumentationTemplatePath() {
        DocumentationGeneratorProperties properties = new DocumentationGeneratorProperties();
        properties.setTemplatePath("classpath:/custom/documentation/");

        SpringResourceTemplateResolver resolver = new DocumentationGeneratorConfig()
                .templateResolver(mock(ApplicationContext.class), properties);

        assertThat(resolver.getPrefix()).isEqualTo("classpath:/custom/documentation/");
    }

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
