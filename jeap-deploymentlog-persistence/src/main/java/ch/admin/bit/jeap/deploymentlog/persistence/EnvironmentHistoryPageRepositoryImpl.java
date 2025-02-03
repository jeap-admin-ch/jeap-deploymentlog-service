package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentHistoryPage;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentHistoryPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentHistoryPageRepositoryImpl implements EnvironmentHistoryPageRepository {

    private final JpaEnvironmentHistoryPageRepository jpaEnvironmentHistoryPageRepository;

    @Override
    public EnvironmentHistoryPage save(EnvironmentHistoryPage environmentHistoryPage) {
        return jpaEnvironmentHistoryPageRepository.save(environmentHistoryPage);
    }

    @Override
    public Optional<EnvironmentHistoryPage> findEnvironmentHistoryPageBySystemIdAndEnvironmentId(UUID systemId, UUID environmentId) {
        return jpaEnvironmentHistoryPageRepository.findEnvironmentHistoryPageBySystemIdAndEnvironmentId(systemId, environmentId);
    }

    @Override
    public void deleteEnvironmentHistoryPageBySystemId(UUID systemId) {
        jpaEnvironmentHistoryPageRepository.deleteEnvironmentHistoryPageBySystemId(systemId);
    }
}
