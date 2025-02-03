package ch.admin.bit.jeap.deploymentlog.jira.dto;

import lombok.Data;

@Data
public class JiraIssueDto {
    String key;
    JiraFieldsDto fields;
}
