package ch.admin.bit.jeap.deploymentlog.jira;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@EnableRetry
public class JiraWebClientConfig {

    @Bean
    @ConditionalOnMissingBean
    JiraWebClient jiraWebClient(JiraWebClientProperties props, @Value("${jeap.deploymentlog.documentation.root-url}") String documentationRootUrl,
                                RestClient.Builder restClientBuilder) {
        if (props.isMockJiraClient()) {
            return new JiraWebClientMock();
        } else {
            return new JiraWebClientImpl(props, documentationRootUrl, restClientBuilder);
        }
    }
}
