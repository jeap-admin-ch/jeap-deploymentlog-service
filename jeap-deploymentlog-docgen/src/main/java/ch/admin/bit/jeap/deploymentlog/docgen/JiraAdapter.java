package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.GeneratedDeploymentPageDto;
import ch.admin.bit.jeap.deploymentlog.jira.JiraWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class JiraAdapter {

    private final JiraWebClient jiraWebClient;

    public void updateJiraIssuesWithConfluenceLink(GeneratedDeploymentPageDto generatedDeploymentPageDto){
        updateJiraIssuesWithConfluenceLink(generatedDeploymentPageDto.getDeploymentLetterPageDto().getChangeJiraIssueKeys(), generatedDeploymentPageDto.getPageId());
    }

    public void updateJiraIssuesWithConfluenceLink(Set<String> jiraIssueKeys, String pageId) {
        jiraIssueKeys.forEach(jiraIssueKey -> {
            try {
                jiraWebClient.updateIssueWithConfluenceLink(jiraIssueKey, pageId);
            } catch (Exception e) {
                log.warn("Ignore exception when updating jira issue: '{}' ", e.getMessage());
            }
        });
    }
}
