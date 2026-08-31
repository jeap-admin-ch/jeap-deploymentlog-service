package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DocumentationStructurePage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaDocumentationStructurePageRepository extends CrudRepository<DocumentationStructurePage, String> {
}
