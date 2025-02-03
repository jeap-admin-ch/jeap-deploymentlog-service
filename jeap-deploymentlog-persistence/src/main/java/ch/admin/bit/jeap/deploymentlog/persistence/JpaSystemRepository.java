package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaSystemRepository extends CrudRepository<System, UUID> {

    Optional<System> findByNameIgnoreCase(String name);

    @Query("select system.id from System system")
    List<UUID> getAllSystemIds();
}
