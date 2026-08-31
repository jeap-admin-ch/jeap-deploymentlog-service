package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;
import java.util.List;

interface JpaComponentPageRepository extends CrudRepository<ComponentPage, UUID> {
    List<ComponentPage> findByParentPageId(String parentPageId);
}
