package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;

public interface JiraIssuePageRepository {
    Optional<JiraIssuePage> findByIssueKey(String issueKey);

    JiraIssuePage save(JiraIssuePage page);
}
