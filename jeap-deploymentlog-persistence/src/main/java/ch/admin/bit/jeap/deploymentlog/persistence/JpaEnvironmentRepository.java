package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface JpaEnvironmentRepository extends CrudRepository<Environment, UUID> {

    Optional<Environment> findByName(String name);

    @Query("""
            select distinct e from System s, Component c, ComponentVersion cv, Deployment d, Environment e \
            where s.id = ?1 \
            AND c.system.id = s.id \
            AND cv.component.id = c.id \
            AND d.componentVersion.id = cv.id \
            AND d.environment.id = e.id order by e.stagingOrder
            """)
    List<Environment> findEnvironmentsForSystem(UUID systemId);

}
