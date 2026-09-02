package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.List;

@Value
@Builder
public class JiraProjectPageDto {
    String projectKey;
    Duration activityPeriod;
    List<JiraProjectIssueDto> issues;
}
