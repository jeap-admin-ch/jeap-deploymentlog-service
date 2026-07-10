package ch.admin.bit.jeap.deploymentlog.jira;

import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraIssueDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraProjectDto;
import ch.admin.bit.jeap.deploymentlog.jira.dto.JiraSearchResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class JiraWebClientImpl implements JiraWebClient {

    /**
     * Syntactically valid jira issue keys: project key (starting with a letter, at least two characters),
     * a dash and the issue number. Anything else is never sent to jira and directly reported as not found.
     */
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");
    private static final int SEARCH_CHUNK_SIZE = 50;
    private static final Duration VISIBLE_PROJECTS_CACHE_TTL = Duration.ofMinutes(5);

    private static final ObjectMapper objectMapper = new JsonMapper();
    private final RestClient restClient;
    private final String documentationRootUrl;
    private final String appId;
    private final AtomicReference<CachedProjects> visibleProjectsCache = new AtomicReference<>();

    private record CachedProjects(Set<String> projectKeys, Instant fetchedAt) {
    }

    public JiraWebClientImpl(JiraWebClientProperties props, String documentationRootUrl, RestClient.Builder restClientBuilder) {
        this.documentationRootUrl = documentationRootUrl;
        this.appId = props.getAppId();
        this.restClient = restClientBuilder
                .defaultHeaders(header -> header.setBasicAuth(props.getUsername(), props.getPassword()))
                .baseUrl(
                        UriComponentsBuilder
                                .fromUriString(props.getUrl())
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
    public JiraIssuesSearchResult searchIssuesLabels(Set<String> jiraIssueKeys) {
        SortedSet<String> notFoundIssueKeys = new TreeSet<>();
        List<String> validIssueKeys = new ArrayList<>();
        jiraIssueKeys.stream()
                .filter(Objects::nonNull)
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .filter(key -> !key.isEmpty())
                .distinct()
                .forEach(key -> {
                    if (ISSUE_KEY_PATTERN.matcher(key).matches()) {
                        validIssueKeys.add(key);
                    } else {
                        log.debug("Not looking up syntactically invalid jira issue key '{}'", key);
                        notFoundIssueKeys.add(key);
                    }
                });

        Map<String, List<String>> labelsByIssueKey = new TreeMap<>();
        for (int fromIndex = 0; fromIndex < validIssueKeys.size(); fromIndex += SEARCH_CHUNK_SIZE) {
            List<String> chunk = validIssueKeys.subList(fromIndex, Math.min(fromIndex + SEARCH_CHUNK_SIZE, validIssueKeys.size()));
            labelsByIssueKey.putAll(searchIssuesLabelsChunk(chunk));
        }
        validIssueKeys.stream()
                .filter(key -> !labelsByIssueKey.containsKey(key))
                .forEach(notFoundIssueKeys::add);

        log.debug("Received jira issues with labels '{}', issue keys not resolved in jira: '{}'", labelsByIssueKey, notFoundIssueKeys);
        return JiraIssuesSearchResult.builder()
                .labelsByIssueKey(labelsByIssueKey)
                .notFoundIssueKeys(notFoundIssueKeys)
                .build();
    }

    private Map<String, List<String>> searchIssuesLabelsChunk(List<String> issueKeys) {
        // Issue keys are quoted to avoid JQL parsing errors for keys resembling JQL reserved words (e.g. AND-1).
        // The keys are validated against ISSUE_KEY_PATTERN and can therefore not break out of the quotes.
        final String jql = issueKeys.stream()
                .map(key -> "\"" + key + "\"")
                .collect(Collectors.joining(",", "key in (", ")"));
        // validateQuery=false makes jira silently ignore issue keys that do not exist or are not readable
        // for the deployment log jira user, instead of rejecting the whole query with a 400 response.
        final Map<String, Object> searchRequest = Map.of(
                "jql", jql,
                "fields", List.of("key", "labels"),
                "validateQuery", false,
                "maxResults", issueKeys.size());

        log.debug("Call jira api with url '/search' and jql '{}'", jql);
        final String action = "searching the labels of the jira issues " + issueKeys;
        try {
            final JiraSearchResultDto searchResult = restClient
                    .post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(searchRequest))
                    .retrieve()
                    .body(JiraSearchResultDto.class);
            if (searchResult == null || searchResult.getIssues() == null) {
                // A 2xx response without a result body is a fundamental jira problem. It must be mapped
                // explicitly: an unclassified exception (e.g. a NullPointerException) would neither be
                // retried nor translated into a meaningful response by the exception handling.
                throw JiraUnavailableException.emptyJiraResponse(action);
            }
            return searchResult.getIssues().stream()
                    .filter(JiraWebClientImpl::hasKey)
                    .collect(Collectors.toMap(
                            issue -> issue.getKey().toUpperCase(Locale.ROOT),
                            JiraWebClientImpl::labels));
        } catch (HttpClientErrorException e) {
            throw JiraUnavailableException.jiraClientError(action, e);
        }
    }

    private static boolean hasKey(JiraIssueDto issue) {
        if (issue.getKey() == null) {
            log.warn("Ignoring malformed jira issue without key in the jira search response");
            return false;
        }
        return true;
    }

    private static List<String> labels(JiraIssueDto issue) {
        if (issue.getFields() == null || issue.getFields().getLabels() == null) {
            return List.of();
        }
        return issue.getFields().getLabels();
    }

    @Override
    public Set<String> getVisibleProjectKeys() {
        CachedProjects cached = visibleProjectsCache.get();
        if (cached != null && cached.fetchedAt().plus(VISIBLE_PROJECTS_CACHE_TTL).isAfter(Instant.now())) {
            return cached.projectKeys();
        }

        log.debug("Call jira api with url '/project'");
        final String action = "fetching the jira projects visible to the deployment log jira user";
        try {
            JiraProjectDto[] projects = restClient
                    .get()
                    .uri("/project")
                    .retrieve()
                    .body(JiraProjectDto[].class);
            if (projects == null) {
                throw JiraUnavailableException.emptyJiraResponse(action);
            }
            Set<String> projectKeys = Arrays.stream(projects)
                    .map(JiraProjectDto::getKey)
                    .filter(Objects::nonNull)
                    .map(key -> key.toUpperCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            log.debug("Fetched {} jira projects visible to the deployment log jira user", projectKeys.size());
            visibleProjectsCache.set(new CachedProjects(projectKeys, Instant.now()));
            return projectKeys;
        } catch (HttpClientErrorException e) {
            throw JiraUnavailableException.jiraClientError(action, e);
        }
    }
}
