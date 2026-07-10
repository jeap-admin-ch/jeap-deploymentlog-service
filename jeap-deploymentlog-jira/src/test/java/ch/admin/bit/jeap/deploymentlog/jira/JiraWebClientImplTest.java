package ch.admin.bit.jeap.deploymentlog.jira;

import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraFieldsDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraIssueDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraSearchResultDto;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JiraWebClientImplTest {

    private JiraWebClient jiraWebClient;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        JiraWebClientProperties props = new JiraWebClientProperties();
        props.setUrl("https://jira-test.com");
        props.setAppId("12345");
        props.setUsername("usr");
        props.setPassword("pwd");
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        jiraWebClient = new JiraWebClientImpl(props, "https:/my-root-url.ch?pageId=", restClientBuilder);
    }

    @Test
    void testUpdateIssueWithConfluenceLink() {
        final String expectedValue = """
                { "application": { "type": "com.atlassian.confluence", "name": "Confluence" },
                  "relationship": "mentioned in",
                  "globalId": "appId=12345&pageId=pageId",
                  "object": { "url": "https:/my-root-url.ch?pageId=pageId", "title": "Page" }
                }""";
        server.expect(requestTo("https://jira-test.com/rest/api/2/issue/issue/remotelink")).
                andExpect(method(HttpMethod.POST)).
                andExpect(content().contentType(MediaType.APPLICATION_JSON)).
                andExpect(content().json(expectedValue)).
                andRespond(withSuccess());
        jiraWebClient.updateIssueWithConfluenceLink("issue", "pageId");
    }

    @Test
    void testSearchIssuesLabels() {
        // Issue keys are quoted in the JQL, the query is not validated and maxResults matches the key count
        final String expectedRequestBody = """
                { "jql": "key in (\\"JEAP-1234\\")", "fields": ["key", "labels"], "validateQuery": false, "maxResults": 1 }""";
        final String responseBody = getJiraSearchResultDtoJson(Map.of("JEAP-1234", List.of("myLabel")));
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andExpect(method(HttpMethod.POST)).
                andExpect(content().contentType(MediaType.APPLICATION_JSON)).
                andExpect(content().json(expectedRequestBody)).
                andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234"));

        assertThat(result.getLabelsByIssueKey()).containsOnlyKeys("JEAP-1234");
        assertThat(result.getLabelsByIssueKey().get("JEAP-1234")).containsOnly("myLabel");
        assertThat(result.getNotFoundIssueKeys()).isEmpty();
    }

    @Test
    void testSearchIssuesLabels_normalizesKeysToUppercase() {
        final String expectedRequestBody = """
                { "jql": "key in (\\"JEAP-1234\\")" }""";
        final String responseBody = getJiraSearchResultDtoJson(Map.of("JEAP-1234", List.of("myLabel")));
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andExpect(content().json(expectedRequestBody)).
                andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(Set.of(" jeap-1234 "));

        assertThat(result.getLabelsByIssueKey()).containsOnlyKeys("JEAP-1234");
        assertThat(result.getNotFoundIssueKeys()).isEmpty();
    }

    @Test
    void testSearchIssuesLabels_reportsIssuesMissingInTheResponseAsNotFound() {
        // With validateQuery=false, jira silently omits unknown/not readable issues instead of failing
        final String responseBody = getJiraSearchResultDtoJson(Map.of("JEAP-1234", List.of("myLabel")));
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234", "JEAP-9999", "AND-1"));

        assertThat(result.getLabelsByIssueKey()).containsOnlyKeys("JEAP-1234");
        assertThat(result.getNotFoundIssueKeys()).containsExactly("AND-1", "JEAP-9999");
    }

    @Test
    void testSearchIssuesLabels_invalidKeysAreNotSentToJiraAndReportedAsNotFound() {
        final String expectedRequestBody = """
                { "jql": "key in (\\"JEAP-1234\\")" }""";
        final String responseBody = getJiraSearchResultDtoJson(Map.of("JEAP-1234", List.of("myLabel")));
        server.expect(once(), requestTo("https://jira-test.com/rest/api/2/search")).
                andExpect(content().json(expectedRequestBody)).
                andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(
                Set.of("JEAP-1234", "A-1", "NOT A KEY-1", "-42", "JEAP-", "1234-5678"));

        assertThat(result.getLabelsByIssueKey()).containsOnlyKeys("JEAP-1234");
        assertThat(result.getNotFoundIssueKeys()).containsExactly("-42", "1234-5678", "A-1", "JEAP-", "NOT A KEY-1");
        server.verify();
    }

    @Test
    void testSearchIssuesLabels_emptyInputDoesNotCallJira() {
        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(Set.of());

        assertThat(result.getLabelsByIssueKey()).isEmpty();
        assertThat(result.getNotFoundIssueKeys()).isEmpty();
        server.verify();
    }

    @Test
    void testSearchIssuesLabels_manyKeysAreSearchedInChunks() {
        Set<String> keys = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "JEAP-" + i)
                .collect(Collectors.toSet());
        server.expect(times(2), requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withSuccess("{\"issues\":[]}", MediaType.APPLICATION_JSON));

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(keys);

        assertThat(result.getLabelsByIssueKey()).isEmpty();
        assertThat(result.getNotFoundIssueKeys()).hasSize(51);
        server.verify();
    }

    @Test
    void testSearchIssuesLabels_clientErrorThrowsJiraUnavailableException() {
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"errorMessages\":[\"auth failed\"]}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234")))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("configuration of the deployment log service")
                .hasMessageContaining("auth failed");
    }

    @Test
    void testSearchIssuesLabels_emptyResponseBodyThrowsJiraUnavailableException() {
        // A 2xx response without a body must not escape as an unclassified NullPointerException
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withSuccess());

        assertThatThrownBy(() -> jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234")))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("empty or unparseable response");
    }

    @Test
    void testSearchIssuesLabels_badRequestThrowsJiraUnavailableException() {
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withBadRequest().body("{\"errorMessages\":[\"unexpected\"]}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234")))
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("400");
    }

    @Test
    void testGetVisibleProjectKeys() {
        server.expect(once(), requestTo("https://jira-test.com/rest/api/2/project")).
                andExpect(method(HttpMethod.GET)).
                andRespond(withSuccess("""
                        [ {"key": "JEAP", "name": "jEAP", "id": "1"}, {"key": "ABC", "name": "Abc", "id": "2"} ]""",
                        MediaType.APPLICATION_JSON));

        Set<String> projectKeys = jiraWebClient.getVisibleProjectKeys();
        assertThat(projectKeys).containsExactlyInAnyOrder("JEAP", "ABC");

        // Second call is served from the cache without calling jira again
        Set<String> cachedProjectKeys = jiraWebClient.getVisibleProjectKeys();
        assertThat(cachedProjectKeys).containsExactlyInAnyOrder("JEAP", "ABC");
        server.verify();
    }

    @Test
    void testGetVisibleProjectKeys_clientErrorThrowsJiraUnavailableException() {
        server.expect(requestTo("https://jira-test.com/rest/api/2/project")).
                andRespond(withStatus(HttpStatus.FORBIDDEN).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> jiraWebClient.getVisibleProjectKeys())
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("403");
    }

    @Test
    void testGetVisibleProjectKeys_emptyResponseBodyThrowsJiraUnavailableException() {
        server.expect(requestTo("https://jira-test.com/rest/api/2/project")).
                andRespond(withSuccess());

        assertThatThrownBy(() -> jiraWebClient.getVisibleProjectKeys())
                .isInstanceOf(JiraUnavailableException.class)
                .hasMessageContaining("empty or unparseable response");
    }

    @SneakyThrows
    private String getJiraSearchResultDtoJson(Map<String, List<String>> labelsByIssueKey) {
        JiraSearchResultDto searchResultDto = new JiraSearchResultDto();
        searchResultDto.setIssues(labelsByIssueKey.entrySet().stream().map(entry -> {
            JiraIssueDto issueDto = new JiraIssueDto();
            issueDto.setKey(entry.getKey());
            JiraFieldsDto fields = new JiraFieldsDto();
            fields.setLabels(entry.getValue());
            issueDto.setFields(fields);
            return issueDto;
        }).toList());
        ObjectMapper objectMapper = new JsonMapper();
        return objectMapper.writeValueAsString(searchResultDto);
    }
}
