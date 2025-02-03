package ch.admin.bit.jeap.deploymentlog.jira;

import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraErrorResponse;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraIssueDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraSearchResultDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class JiraWebClientImpl implements JiraWebClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern pattern = Pattern.compile("'(.*?)'");
    private final RestClient restClient;
    private final String documentationRootUrl;
    private final String appId;

    public JiraWebClientImpl(JiraWebClientProperties props, String documentationRootUrl, RestClient.Builder restClientBuilder) {
        this.documentationRootUrl = documentationRootUrl;
        this.appId = props.getAppId();
        this.restClient = restClientBuilder
                .defaultHeaders(header -> header.setBasicAuth(props.getUsername(), props.getPassword()))
                .baseUrl(
                        UriComponentsBuilder
                                .fromHttpUrl(props.getUrl())
                                .pathSegment("rest", "api", "2")
                                .build()
                                .toString())
                .build();
    }

    @Override
    public void updateIssueWithConfluenceLink(String jiraIssueKey, String pageId) {
        final String url = "/issue/" + jiraIssueKey + "/remotelink";
        log.debug("Call jira api with url '{}' and pageId '{}'", url, pageId);

        final String confluenceLink = documentationRootUrl + pageId;

        final String updateIssueBodyValue = """
                {\
                    "application": {\
                        "type": "com.atlassian.confluence",\
                        "name": "Confluence"\
                    },\
                    "relationship": "mentioned in",\
                    "globalId": "appId=%3$s&pageId=%1$s",\
                    "object": {\
                        "url": "%2$s",\
                        "title": "Page"\
                    }\
                }\
                """;

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateIssueBodyValue.formatted(pageId, confluenceLink, appId))
                .retrieve()
                .toBodilessEntity();
        log.info("Jira issue '{}' updated with confluence link '{}'", jiraIssueKey, confluenceLink);

    }

    @Override
    public Map<String, List<String>> searchIssuesLabels(Set<String> jiraIssueKeys) throws JiraIssuesNotFoundException {
        if (jiraIssueKeys.isEmpty()) {
            return Collections.emptyMap();
        }

        final String url = "/search";
        log.debug("Call jira api with url '{}' and jiraIssueKeys '{}'", url, jiraIssueKeys);

        final String body = "{ \"jql\": \"key in (%1$s)\", \"fields\":[\"key\", \"labels\"] }";

        try {
            final JiraSearchResultDto jiraSearchResultDto = restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.formatted(String.join(",", jiraIssueKeys)))
                    .exchange( (clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                            throw mapToException(clientResponse.bodyTo(String.class));
                        } else {
                            return Objects.requireNonNull(clientResponse.bodyTo(JiraSearchResultDto.class),
                                    "result of api call is null!");
                        }
                    });

            log.trace("Received response {}", jiraSearchResultDto);
            Map<String, List<String>> issuesWithLabels = jiraSearchResultDto.getIssues().stream().collect(
                    Collectors.toMap(JiraIssueDto::getKey, issue -> issue.getFields().getLabels()));

            log.debug("Received jira issues with labels '{}'", issuesWithLabels);
            return issuesWithLabels;
        } catch (Exception e) {
            if (e.getCause() instanceof JiraIssuesNotFoundException jiraIssuesNotFoundException) {
                throw jiraIssuesNotFoundException;
            }
            throw e;
        }
    }

    private JiraIssuesNotFoundException mapToException(String errorMessageJson) {
        try {
            final List<String> jiraIssues = new ArrayList<>();
            for (String errorMessage : objectMapper.readValue(errorMessageJson, JiraErrorResponse.class).getErrorMessages()) {
                Matcher matcher = pattern.matcher(errorMessage);
                if (matcher.find())
                {
                    jiraIssues.add(matcher.group(1));
                }
            }
            return new JiraIssuesNotFoundException(jiraIssues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
