package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableConfigurationProperties(value = JiraWebClientProperties.class)
class JiraWebClientImplIT {


    public static final String JIRA_URL = "https://<<your-jira>>";
    public static final String CONFLUENCE_URL = "https://<<your-confluence>>/pages/viewpage.action?pageId=";

    private final JiraWebClientProperties properties = new JiraWebClientProperties();
    private final RestClient.Builder restClientBuilder = RestClient.builder();

    @BeforeEach
    void setup(){
        properties.setUsername("replace");
        properties.setPassword("replace");
        properties.setUrl(JIRA_URL);
        properties.setAppId("a98a4024-8c94-3bbc-820c-14694c7a64ad");
    }

    @Test
    @Disabled("Only use manually to test the jira api")
    void updateIssueWithConfluenceLink() {
        JiraWebClient jiraWebClient = new JiraWebClientImpl(properties, CONFLUENCE_URL, restClientBuilder);
        jiraWebClient.updateIssueWithConfluenceLink("JEAP-3248", "416886357");
        assertThat(properties).isNotNull();
    }

    @Test
    @Disabled("Only use manually to test the jira api")
    @SneakyThrows
    void searchIssuesLabels() {
        JiraWebClient jiraWebClient = new JiraWebClientImpl(properties, CONFLUENCE_URL, restClientBuilder);
        final Map<String, List<String>> results = jiraWebClient.searchIssuesLabels(Set.of("JEAP-3248", "JEAP-3222", "JEAP-3190"));
        assertThat(results).isNotNull();
        assertThat(results.entrySet()).hasSize(3);
    }

    @Test
    @Disabled("Only use manually to test the jira api")
    void searchIssuesLabels_error() {
        JiraWebClient jiraWebClient = new JiraWebClientImpl(properties, CONFLUENCE_URL, restClientBuilder);
        JiraIssuesNotFoundException exception = assertThrows(JiraIssuesNotFoundException.class, () -> {
            jiraWebClient.searchIssuesLabels(Set.of("JEAP-9999", "JEAP-1234", "JEAP-23099"));
        });
        assertThat(exception.getIssues()).hasSize(2);
        assertThat(exception.getIssues()).contains("JEAP-9999", "JEAP-23099");
    }
}
