package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPage;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageQueryResult;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaDeploymentPageRepository extends CrudRepository<DeploymentPage, UUID> {

    Optional<DeploymentPage> findDeploymentPageByDeploymentId(UUID deploymentId);

    @Query("""
            select p from DeploymentPage p \
            inner join Deployment d on p.deploymentId = d.id \
            inner join ComponentVersion cv on d.componentVersion = cv \
            inner join Component c on cv.component = c \
            inner join System s on c.system = s \
            where s.id = :systemId and d.environment in :envs \
            order by p.deploymentStateTimestamp desc
            """)
    List<DeploymentPage> getDeploymentPagesForEnvironments(@Param("envs") List<Environment> envs, @Param("systemId") UUID systemId);

    @Query("""
            select new ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageQueryResult(d.id, p.pageId) from DeploymentPage p \
            inner join Deployment d on p.deploymentId = d.id \
            inner join ComponentVersion cv on d.componentVersion = cv \
            inner join Component c on cv.component = c \
            inner join System s on c.system = s \
            where s.id = :systemId and p.pageId is not null
            """)
    List<DeploymentPageQueryResult> getDeploymentPagesForSystem(@Param("systemId") UUID systemId);
}
