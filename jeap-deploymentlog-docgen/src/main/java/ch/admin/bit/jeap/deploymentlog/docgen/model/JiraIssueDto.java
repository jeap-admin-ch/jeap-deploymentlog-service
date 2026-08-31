package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JiraIssueDto {
    String key;
    String url;
}
