package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;
import java.util.List;

public interface JiraProjectPageRepository {

    Optional<JiraProjectPage> findByProjectKey(String projectKey);

    JiraProjectPage save(JiraProjectPage page);

    List<JiraProjectPage> findAll();
}
