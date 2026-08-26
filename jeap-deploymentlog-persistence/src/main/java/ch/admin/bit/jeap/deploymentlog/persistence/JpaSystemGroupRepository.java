package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaSystemGroupRepository extends JpaRepository<SystemGroup, UUID> {

    Optional<SystemGroup> findByNormalizedName(String normalizedName);

    List<SystemGroup> findAllByOrderByNormalizedNameAscIdAsc();
}
