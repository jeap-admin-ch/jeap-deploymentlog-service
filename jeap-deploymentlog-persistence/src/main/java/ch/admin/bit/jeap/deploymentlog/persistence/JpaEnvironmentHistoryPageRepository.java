package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentHistoryPage;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaEnvironmentHistoryPageRepository extends CrudRepository<EnvironmentHistoryPage, UUID> {

    Optional<EnvironmentHistoryPage> findEnvironmentHistoryPageBySystemIdAndEnvironmentId(UUID systemId, UUID environmentId);
    void deleteEnvironmentHistoryPageBySystemId(UUID systemId);

}
