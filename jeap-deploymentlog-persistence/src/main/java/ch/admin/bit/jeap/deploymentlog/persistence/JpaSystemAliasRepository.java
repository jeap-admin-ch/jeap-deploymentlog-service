package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemAlias;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaSystemAliasRepository extends CrudRepository<SystemAlias, UUID> {

    Optional<SystemAlias> findByName(String name);
}
