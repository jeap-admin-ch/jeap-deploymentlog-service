package ch.admin.bit.jeap.deploymentlog.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusProperties;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusCreatedTimestampDefaultTest {

    @Test
    void enablesCreatedTimestampsByDefault() {
        PrometheusProperties properties = new PrometheusProperties();

        processor(new MockEnvironment(), classLoaderWithoutPrometheusProperties())
                .postProcessBeforeInitialization(properties, "prometheusProperties");

        assertThat(properties.getProperties())
                .containsEntry(PrometheusCreatedTimestampDefault.PROMETHEUS_KEY, "true");
    }

    @Test
    void preservesExplicitSpringOptOut() {
        PrometheusProperties properties = new PrometheusProperties();
        properties.getProperties().put(PrometheusCreatedTimestampDefault.PROMETHEUS_KEY, "false");

        processor(new MockEnvironment(), classLoaderWithoutPrometheusProperties())
                .postProcessBeforeInitialization(properties, "prometheusProperties");

        assertThat(properties.getProperties())
                .containsEntry(PrometheusCreatedTimestampDefault.PROMETHEUS_KEY, "false");
    }

    @Test
    void preservesExplicitClasspathOptOut() {
        PrometheusProperties properties = new PrometheusProperties();
        ClassLoader classLoader = new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("prometheus.properties".equals(name)) {
                    return new ByteArrayInputStream(
                            (PrometheusCreatedTimestampDefault.PROMETHEUS_KEY + "=false")
                                    .getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }
        };

        processor(new MockEnvironment(), classLoader)
                .postProcessBeforeInitialization(properties, "prometheusProperties");

        assertThat(properties.getProperties()).doesNotContainKey(PrometheusCreatedTimestampDefault.PROMETHEUS_KEY);
    }

    private PrometheusCreatedTimestampDefault processor(MockEnvironment environment, ClassLoader classLoader) {
        return new PrometheusCreatedTimestampDefault(environment, classLoader);
    }

    private ClassLoader classLoaderWithoutPrometheusProperties() {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return null;
            }
        };
    }
}
