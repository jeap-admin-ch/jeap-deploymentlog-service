package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.UUID;

public interface ReferenceRepository {

    boolean existsById(UUID id);

    List<Reference> findAllByReferenceIdentifier(String referenceIdentifier);

    void save(Reference build);
}
