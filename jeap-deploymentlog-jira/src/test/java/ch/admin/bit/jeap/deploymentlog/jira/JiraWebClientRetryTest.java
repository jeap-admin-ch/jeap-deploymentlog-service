package ch.admin.bit.jeap.deploymentlog.jira;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * Verifies the retry behavior declared via {@code @Retryable} on {@link JiraWebClient}: server errors are
 * retried, while client errors (mapped to {@link JiraUnavailableException}) are not.
 */
class JiraWebClientRetryTest {

    private MockRestServiceServer server;
    private JiraWebClient jiraWebClient;
    private AnnotationConfigApplicationContext context;

    @Configuration
    @EnableRetry
    static class RetryTestConfig {
    }

    @BeforeEach
    void setUp() {
        JiraWebClientProperties props = new JiraWebClientProperties();
        props.setUrl("https://jira-test.com");
        props.setAppId("12345");
        props.setUsername("usr");
        props.setPassword("pwd");
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        context = new AnnotationConfigApplicationContext();
        // Speed up the test: reduce the retry backoff delay to 1ms
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("jeap.deploymentlog.jira.retry-delay-ms", "1")));
        context.register(RetryTestConfig.class);
        context.registerBean(JiraWebClient.class,
                () -> new JiraWebClientImpl(props, "https:/my-root-url.ch?pageId=", restClientBuilder));
        context.refresh();
        jiraWebClient = context.getBean(JiraWebClient.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void searchIssuesLabels_serverErrorIsRetried4Times() {
        server.expect(times(4), requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withServerError());

        assertThatThrownBy(() -> jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234")))
                .isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    @Test
    void searchIssuesLabels_clientErrorIsNotRetried() {
        server.expect(once(), requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234")))
                .isInstanceOf(JiraUnavailableException.class);
        server.verify();
    }
}
