package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual integration test against a real jira instance, e.g. to verify on the jira version in use that
 * {@code validateQuery=false} silently ignores unresolvable issue keys and that the project list endpoint
 * returns the projects visible to the technical jira user.
 * <p>
 * All REST calls go to the jira base url ({@code JIRA_IT_URL}). The documentation root url passed to the
 * client is only used as the <em>content</em> of the confluence link written into a jira issue by
 * {@code updateIssueWithConfluenceLink} - it is irrelevant for the search and project tests.
 * <p>
 * The tests are skipped unless the following environment variables are set, so no source code changes are
 * needed (and no credentials can end up in version control):
 *
 * <pre>
 * export JIRA_IT_URL=<your-jira-base-url>
 * export JIRA_IT_USERNAME=your-technical-user
 * export JIRA_IT_PASSWORD='...'
 * export JIRA_IT_EXISTING_ISSUE=JEAP-7261            # any issue readable by the technical user
 * export JIRA_IT_NOT_VISIBLE_ISSUE=SECRET-1          # optional: issue in a project not visible to the user
 * export JIRA_IT_UPDATE_ISSUE=JEAP-9999              # optional: CAUTION - writes a remote link to this issue
 * export JIRA_IT_UPDATE_PAGE_ID=416886357            # page id for the remote link written by the update test
 *
 * ./mvnw -pl jeap-deploymentlog-jira verify -Dit.test=JiraWebClientImplIT
 * </pre>
 *
 * Alternatively, run the class directly from the IDE with the environment variables set.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "JIRA_IT_URL", matches = ".+")
class JiraWebClientImplIT {

    private JiraWebClient jiraWebClient;
    private String existingIssue;
    private String existingProjectKey;

    @BeforeEach
    void setup() {
        JiraWebClientProperties properties = new JiraWebClientProperties();
        properties.setUrl(requireEnv("JIRA_IT_URL"));
        properties.setUsername(requireEnv("JIRA_IT_USERNAME"));
        properties.setPassword(requireEnv("JIRA_IT_PASSWORD"));
        properties.setAppId(env("JIRA_IT_CONFLUENCE_APP_ID", "a98a4024-8c94-3bbc-820c-14694c7a64ad"));
        // Only used as link content by updateIssueWithConfluenceLink, never called as a URL by the client
        String documentationRootUrl = env("JIRA_IT_DOCUMENTATION_ROOT_URL",
                "https://confluence.example.com/pages/viewpage.action?pageId=");
        jiraWebClient = new JiraWebClientImpl(properties, documentationRootUrl, RestClient.builder());

        existingIssue = requireEnv("JIRA_IT_EXISTING_ISSUE").trim().toUpperCase(Locale.ROOT);
        existingProjectKey = projectKeyOf(existingIssue);
    }

    @Test
    void searchIssuesLabels_existingIssue_isFound() {
        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(Set.of(existingIssue));

        log.info("Search result for existing issue {}: {}", existingIssue, result);
        assertThat(result.getLabelsByIssueKey()).containsKey(existingIssue);
        assertThat(result.getNotFoundIssueKeys()).isEmpty();
    }

    @Test
    void searchIssuesLabels_unresolvableIssueKeys_areReportedAsNotFoundWithoutFailingTheSearch() {
        // Verifies on the real jira instance that validateQuery=false silently ignores issues that do
        // not exist, and that quoted keys resembling JQL reserved words (AND-1) do not break the query
        String nonExistingIssue = existingProjectKey + "-999999999";
        Set<String> issueKeys = Set.of(existingIssue, nonExistingIssue, "AND-1");

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(issueKeys);

        log.info("Search result for {}: {}", issueKeys, result);
        assertThat(result.getLabelsByIssueKey()).containsKey(existingIssue);
        assertThat(result.getNotFoundIssueKeys()).containsExactlyInAnyOrder(nonExistingIssue, "AND-1");
    }

    @Test
    void getVisibleProjectKeys_containsTheProjectOfTheExistingIssue() {
        Set<String> projectKeys = jiraWebClient.getVisibleProjectKeys();

        log.info("{} projects visible to the technical jira user: {}", projectKeys.size(), projectKeys);
        assertThat(projectKeys).contains(existingProjectKey);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JIRA_IT_NOT_VISIBLE_ISSUE", matches = ".+")
    void searchIssuesLabels_issueInProjectNotVisibleToTheUser_isReportedAsNotFound() {
        String notVisibleIssue = requireEnv("JIRA_IT_NOT_VISIBLE_ISSUE").trim().toUpperCase(Locale.ROOT);

        JiraIssuesSearchResult result = jiraWebClient.searchIssuesLabels(Set.of(notVisibleIssue));

        log.info("Search result for not visible issue {}: {}", notVisibleIssue, result);
        assertThat(result.getLabelsByIssueKey()).doesNotContainKey(notVisibleIssue);
        assertThat(result.getNotFoundIssueKeys()).containsExactly(notVisibleIssue);
        assertThat(jiraWebClient.getVisibleProjectKeys()).doesNotContain(projectKeyOf(notVisibleIssue));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JIRA_IT_UPDATE_ISSUE", matches = ".+")
    void updateIssueWithConfluenceLink_writesRemoteLinkToTheIssue() {
        // CAUTION: this test writes a remote link into the given jira issue
        jiraWebClient.updateIssueWithConfluenceLink(requireEnv("JIRA_IT_UPDATE_ISSUE"), requireEnv("JIRA_IT_UPDATE_PAGE_ID"));
    }

    private static String projectKeyOf(String issueKey) {
        return issueKey.substring(0, issueKey.indexOf('-'));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        assertThat(value).as("Environment variable '%s' must be set to run this test", name).isNotBlank();
        return value;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
