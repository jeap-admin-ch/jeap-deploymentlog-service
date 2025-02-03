package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.Optional;
import java.util.UUID;

public interface EnvironmentHistoryPageRepository {

    EnvironmentHistoryPage save(EnvironmentHistoryPage environmentHistoryPage);
    Optional<EnvironmentHistoryPage> findEnvironmentHistoryPageBySystemIdAndEnvironmentId(UUID systemId, UUID environmentId);
    void deleteEnvironmentHistoryPageBySystemId(UUID systemId);

}
