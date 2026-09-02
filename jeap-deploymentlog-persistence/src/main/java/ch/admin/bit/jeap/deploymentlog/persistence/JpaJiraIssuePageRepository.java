package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePage;
import org.springframework.data.repository.CrudRepository;

interface JpaJiraIssuePageRepository extends CrudRepository<JiraIssuePage, String> {
}
