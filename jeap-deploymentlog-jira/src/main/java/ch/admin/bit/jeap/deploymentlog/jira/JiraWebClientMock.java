package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class JiraWebClientMock implements JiraWebClient {

    /**
     * Matches {@code DeploymentCheckService.R4DEPLOY_LABEL}: the mock reports every issue as existing
     * and ready for deploy, so the ready-for-deploy check always passes on mocked instances.
     */
    private static final String READY_FOR_DEPLOY_LABEL = "R4DEPLOY";

    @Override
    public void updateIssueWithConfluenceLink(String jiraIssueKey, String pageId){
        log.info("MOCK update jira issue '{}' with pageId '{}'", jiraIssueKey, pageId);
    }

    @Override
    public JiraIssuesSearchResult searchIssuesLabels(Set<String> jiraIssueKeys) {
        log.info("MOCK searchIssuesLabels with jiraIssueKeys '{}'", jiraIssueKeys);
        Map<String, List<String>> labelsByIssueKey = jiraIssueKeys.stream()
                .collect(Collectors.toMap(Function.identity(), _ -> List.of(READY_FOR_DEPLOY_LABEL)));
        return JiraIssuesSearchResult.builder()
                .labelsByIssueKey(labelsByIssueKey)
                .notFoundIssueKeys(Set.of())
                .build();
    }

    @Override
    public Set<String> getVisibleProjectKeys() {
        log.info("MOCK getVisibleProjectKeys");
        return Set.of();
    }
}
