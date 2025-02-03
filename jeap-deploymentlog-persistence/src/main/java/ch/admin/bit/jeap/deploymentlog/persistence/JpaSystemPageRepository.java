package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemPage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaSystemPageRepository extends CrudRepository<SystemPage, UUID> {

    Optional<SystemPage> findSystemPageBySystemId(UUID systemId);
}
