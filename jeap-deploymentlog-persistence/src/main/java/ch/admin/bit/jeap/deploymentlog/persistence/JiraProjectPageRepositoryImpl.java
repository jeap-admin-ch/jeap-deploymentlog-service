package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPage;
import ch.admin.bit.jeap.deploymentlog.domain.JiraProjectPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class JiraProjectPageRepositoryImpl implements JiraProjectPageRepository {

    private final JpaJiraProjectPageRepository repository;

    @Override
    public Optional<JiraProjectPage> findByProjectKey(String projectKey) {
        return repository.findById(projectKey);
    }

    @Override
    public JiraProjectPage save(JiraProjectPage page) {
        return repository.save(page);
    }

    @Override
    public List<JiraProjectPage> findAll() {
        return StreamSupport.stream(repository.findAll().spliterator(), false).toList();
    }
}
