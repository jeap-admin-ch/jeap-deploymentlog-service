package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DocumentationStructurePage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
interface JpaDocumentationStructurePageRepository extends CrudRepository<DocumentationStructurePage, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentationStructurePage> findForUpdateByStructureKey(String structureKey);
}
