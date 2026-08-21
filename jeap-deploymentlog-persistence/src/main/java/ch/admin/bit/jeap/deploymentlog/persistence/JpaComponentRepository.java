package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

import jakarta.persistence.LockModeType;

@Repository
interface JpaComponentRepository extends CrudRepository<Component, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select component from Component component where component.id = :id")
    Optional<Component> lockById(@Param("id") UUID id);
}
