package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersion;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface JpaArtifactVersionRepository extends CrudRepository<ArtifactVersion, UUID> {

    List<ArtifactVersion> findAllByCoordinates(String coordinates);

}
