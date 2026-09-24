package ch.admin.bit.jeap.deploymentlog.domain;

import java.time.ZonedDateTime;

public record DeploymentSearchCriteria(
        ZonedDateTime from,
        ZonedDateTime to,
        String environment,
        String system,
        String component,
        String version,
        String jiraProject,
        String jiraIssue) {
}
