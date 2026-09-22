package ch.admin.bit.jeap.deploymentlog.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusProperties;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Slf4j
class PrometheusCreatedTimestampDefault implements BeanPostProcessor {

    static final String PROMETHEUS_KEY = "io.prometheus.exporter.include_created_timestamps";
    private static final String SPRING_KEY =
            "management.prometheus.metrics.export.properties." + PROMETHEUS_KEY;
    private static final String ENVIRONMENT_KEY = "IO_PROMETHEUS_EXPORTER_INCLUDE_CREATED_TIMESTAMPS";

    private final Environment environment;
    private final ClassLoader classLoader;

    PrometheusCreatedTimestampDefault(Environment environment, ClassLoader classLoader) {
        this.environment = environment;
        this.classLoader = classLoader;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof PrometheusProperties properties
                && !isConfigured(properties.getProperties())
                && !isConfiguredNatively()) {
            properties.getProperties().put(PROMETHEUS_KEY, "true");
        }
        return bean;
    }

    private boolean isConfigured(Map<String, String> springProperties) {
        return springProperties.keySet().stream().anyMatch(this::isCreatedTimestampKey)
                || environment.containsProperty(SPRING_KEY);
    }

    private boolean isConfiguredNatively() {
        if (System.getenv(ENVIRONMENT_KEY) != null
                || System.getProperties().stringPropertyNames().stream().anyMatch(this::isCreatedTimestampKey)) {
            return true;
        }
        if (containsCreatedTimestampProperty(loadClasspathProperties())) {
            return true;
        }
        String externalConfig = System.getenv("PROMETHEUS_CONFIG");
        if (externalConfig == null) {
            externalConfig = System.getProperty("prometheus.config");
        }
        return externalConfig != null && containsCreatedTimestampProperty(loadProperties(Path.of(externalConfig)));
    }

    private Properties loadClasspathProperties() {
        try (InputStream input = classLoader.getResourceAsStream("prometheus.properties")) {
            Properties properties = new Properties();
            if (input != null) {
                properties.load(input);
            }
            return properties;
        } catch (IOException exception) {
            log.warn("Cannot inspect prometheus.properties for an explicit created-timestamp setting", exception);
            return new Properties();
        }
    }

    private Properties loadProperties(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            log.warn("Cannot inspect Prometheus configuration {} for an explicit created-timestamp setting", path,
                    exception);
            return new Properties();
        }
    }

    private boolean containsCreatedTimestampProperty(Properties properties) {
        return properties.stringPropertyNames().stream().anyMatch(this::isCreatedTimestampKey);
    }

    private boolean isCreatedTimestampKey(String key) {
        return normalize(key).equals(normalize(PROMETHEUS_KEY));
    }

    private String normalize(String key) {
        return key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
