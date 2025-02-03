package ch.admin.bit.jeap.deploymentlog.jira.dto;

import lombok.Data;
import java.util.List;

@Data
public class JiraSearchResultDto {
    List<JiraIssueDto> issues;
}
