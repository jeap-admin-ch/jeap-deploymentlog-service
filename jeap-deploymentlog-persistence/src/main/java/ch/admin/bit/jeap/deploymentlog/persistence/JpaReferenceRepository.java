package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Reference;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface JpaReferenceRepository extends CrudRepository<Reference, UUID> {

    List<Reference> findAllByReferenceIdentifier(String referenceIdentifier);
}
