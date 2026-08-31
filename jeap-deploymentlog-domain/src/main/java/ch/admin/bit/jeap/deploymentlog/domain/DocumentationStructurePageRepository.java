package ch.admin.bit.jeap.deploymentlog.domain;

import java.util.List;
import java.util.Optional;

public interface DocumentationStructurePageRepository {

    DocumentationStructurePage save(DocumentationStructurePage page);

    Optional<DocumentationStructurePage> findByStructureKey(String structureKey);

    List<DocumentationStructurePage> findAll();

    void delete(DocumentationStructurePage page);
}
