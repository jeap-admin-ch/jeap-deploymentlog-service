package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import org.springframework.data.repository.CrudRepository;

interface JpaJiraProjectPageRepository extends CrudRepository<JiraProjectPage, String> {
}
