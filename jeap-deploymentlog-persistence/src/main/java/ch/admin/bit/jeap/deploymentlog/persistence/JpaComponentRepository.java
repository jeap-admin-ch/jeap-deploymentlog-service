package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaComponentRepository extends CrudRepository<Component, UUID> {

    @Query(value = "select id from component where id = :id for update", nativeQuery = true)
    Optional<UUID> lockById(@Param("id") UUID id);
}
