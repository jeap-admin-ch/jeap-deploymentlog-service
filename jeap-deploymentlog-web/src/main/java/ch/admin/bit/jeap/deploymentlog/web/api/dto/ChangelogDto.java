package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Data;

import java.util.Set;

@Data
public class ChangelogDto {

    String comparedToVersion;

    String comment;

    /**
     * List of jira issue keys that relate to changes in this version
     */
    Set<String> jiraIssueKeys;

    public Set<String> getJiraIssueKeys() {
        return jiraIssueKeys == null ? Set.of() : jiraIssueKeys;
    }

    public String getComment() {
        return comment == null ? "" : comment;
    }

}
