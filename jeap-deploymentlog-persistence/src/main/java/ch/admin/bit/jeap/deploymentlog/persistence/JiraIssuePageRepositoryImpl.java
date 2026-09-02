package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraIssuePageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JiraIssuePageRepositoryImpl implements JiraIssuePageRepository {

    private final JpaJiraIssuePageRepository repository;

    @Override
    public Optional<JiraIssuePage> findByIssueKey(String issueKey) {
        return repository.findById(issueKey);
    }

    @Override
    public JiraIssuePage save(JiraIssuePage page) {
        return repository.save(page);
    }
}
