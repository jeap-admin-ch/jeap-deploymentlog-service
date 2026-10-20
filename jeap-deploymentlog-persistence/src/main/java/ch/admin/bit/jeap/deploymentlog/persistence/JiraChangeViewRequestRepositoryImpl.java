package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraChangeViewRequest;
import ch.admin.bit.jeap.deploymentlog.domain.JiraChangeViewRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JiraChangeViewRequestRepositoryImpl implements JiraChangeViewRequestRepository {

    private final JpaJiraChangeViewRequestRepository repository;

    @Override
    @Transactional
    public void saveAll(Collection<JiraChangeViewRequest> requests) {
        repository.saveAll(requests);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JiraChangeViewRequest> findByProjectKey(String projectKey) {
        return repository.findByProjectKey(projectKey);
    }

    @Override
    @Transactional
    public void deleteAllById(Collection<UUID> ids) {
        repository.deleteAllById(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findProjectKeysRequestedBefore(ZonedDateTime requestedBefore, int limit) {
        return repository.findProjectKeysRequestedBefore(requestedBefore, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public long countIssueKeysRequestedBefore(ZonedDateTime requestedBefore) {
        return repository.countIssueKeysRequestedBefore(requestedBefore);
    }
}
