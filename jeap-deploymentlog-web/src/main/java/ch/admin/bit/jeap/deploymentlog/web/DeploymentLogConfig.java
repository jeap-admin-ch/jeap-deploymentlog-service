package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

@AutoConfiguration
@EnableConfigurationProperties
@ComponentScan(basePackageClasses = {DeploymentLogApplication.class, DocumentationGenerator.class, JiraWebClient.class})
@PropertySource("classpath:deploymentlogDefaultProperties.properties")
class DeploymentLogConfig {

}
