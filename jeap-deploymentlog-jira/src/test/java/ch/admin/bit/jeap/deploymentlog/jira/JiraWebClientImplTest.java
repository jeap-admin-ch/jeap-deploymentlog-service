package ch.admin.bit.jeap.deploymentlog.jira;

import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraFieldsDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraIssueDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraSearchResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(components = {JiraWebClient.class}, properties = {
        "jeap.deploymentlog.jira.url=https://jira-test.com",
        "jeap.deploymentlog.jira.appId=12345",
        "jeap.deploymentlog.jira.username=usr",
        "jeap.deploymentlog.jira.password=pwd",
        "jeap.deploymentlog.documentation.root-url=https:/my-root-url.ch?pageId="
})
@ContextConfiguration(classes={JiraWebClientConfig.class})
@EnableConfigurationProperties(JiraWebClientProperties.class)
class JiraWebClientImplTest {

    @Autowired
    private JiraWebClient jiraWebClient;

    @Autowired
    private MockRestServiceServer server;

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
        final String expectedRequestBody = "{ \"jql\": \"key in (JEAP-1234)\", \"fields\":[\"key\", \"labels\"] }";
        final String responseBody = getJiraSearchResultDtoJson("JEAP-1234", "myLabel");
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andExpect(method(HttpMethod.POST)).
                andExpect(content().contentType(MediaType.APPLICATION_JSON)).
                andExpect(content().json(expectedRequestBody)).
                andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        Map<String, List<String>> result = jiraWebClient.searchIssuesLabels(Set.of("JEAP-1234"));
        assertThat(result).containsOnlyKeys("JEAP-1234");
        assertThat(result.get("JEAP-1234")).containsOnly("myLabel");
    }

    @SneakyThrows
    private String getJiraSearchResultDtoJson(String issue, String label) {
        JiraSearchResultDto searchResultDto = new JiraSearchResultDto();
        JiraIssueDto issueDto = new JiraIssueDto();
        issueDto.setKey(issue);
        JiraFieldsDto fields = new JiraFieldsDto();
        fields.setLabels(List.of(label));
        issueDto.setFields(fields);
        searchResultDto.setIssues(List.of(issueDto));
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(searchResultDto);
    }

    @Test
    void testSearchIssuesLabelsNotFound() {
        final String errorResponseBody = """
                {\
                    "errorMessages": [\
                        "An issue with key 'JEAP-13190' does not exist for field 'key'.",\
                        "An issue with key 'JEAP-23099' does not exist for field 'key'."\
                    ],\
                    "errors": {}\
                }\
                """;
        server.expect(requestTo("https://jira-test.com/rest/api/2/search")).
                andRespond(withBadRequest().body(errorResponseBody).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(
                () -> jiraWebClient.searchIssuesLabels(Set.of("JEAP-13190", "JEAP-23099")) )
                .isInstanceOf(JiraIssuesNotFoundException.class)
                .hasMessageContaining("One or more issues not found in jira")
                .extracting("issues", InstanceOfAssertFactories.ITERABLE)
                .containsExactly("JEAP-13190", "JEAP-23099");
    }

    @Test
    void testRetry() {
        server.expect(times(4), requestTo("https://jira-test.com/rest/api/2/issue/issue/remotelink")).
                andRespond(withServerError());
        try {
            jiraWebClient.updateIssueWithConfluenceLink("issue", "pageId");
        } catch (HttpServerErrorException.InternalServerError e) {
            // expected as the test exhausts all retries
        }
    }

}
