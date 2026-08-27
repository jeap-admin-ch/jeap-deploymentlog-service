package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaSystemGroupRepository extends JpaRepository<SystemGroup, UUID> {

    @EntityGraph(attributePaths = "systems")
    @Query("select systemGroup from SystemGroup systemGroup where systemGroup.id = :id")
    Optional<SystemGroup> findByIdWithSystems(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select systemGroup from SystemGroup systemGroup where systemGroup.id = :id")
    Optional<SystemGroup> findByIdForUpdate(@Param("id") UUID id);

    Optional<SystemGroup> findByNormalizedName(String normalizedName);

    @EntityGraph(attributePaths = "systems")
    @Query("select systemGroup from SystemGroup systemGroup order by systemGroup.normalizedName, systemGroup.id")
    List<SystemGroup> findAllSortedWithSystems();
}
