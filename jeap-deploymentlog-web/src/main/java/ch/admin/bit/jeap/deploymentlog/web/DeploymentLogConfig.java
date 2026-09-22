package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties
@ComponentScan(basePackageClasses = {DeploymentLogApplication.class, DocumentationGenerator.class, JiraWebClient.class})
@PropertySource("classpath:deploymentlogDefaultProperties.properties")
class DeploymentLogConfig {

    private DeploymentLogConfig() {
    }

    @Bean
    static BeanPostProcessor prometheusCreatedTimestampDefault(Environment environment) {
        return new PrometheusCreatedTimestampDefault(environment,
                Thread.currentThread().getContextClassLoader());
    }

}
