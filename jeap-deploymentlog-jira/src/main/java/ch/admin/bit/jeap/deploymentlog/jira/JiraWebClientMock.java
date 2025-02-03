package ch.admin.bit.jeap.deploymentlog.jira;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class JiraWebClientMock implements JiraWebClient {

    @Override
    public void updateIssueWithConfluenceLink(String jiraIssueKey, String pageId){
        log.info("MOCK update jira issue '{}' with pageId '{}'", jiraIssueKey, pageId);
    }

    @Override
    public Map<String, List<String>> searchIssuesLabels(Set<String> jiraIssueKeys) {
        log.info("MOCK searchIssuesLabels with jiraIssueKeys '{}'", jiraIssueKeys);
        return Collections.emptyMap();
    }
}
