package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.datasource.generate-unique-name=true")
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataRetentionSchemaCompatibilityTest {
    @Autowired private DataRetentionRepository retention;
    @Autowired private DeploymentRepository deployments;
    @Autowired private EnvironmentRepository environments;
    @Autowired private ComponentRepository components;
    @Autowired private SystemRepository systems;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void retainsLegacyReferencedVersionsAndDetectsSchemaRemovalWithoutRestart() {
        var tx = new TransactionTemplate(transactionManager);
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
        Deployment legacy = tx.execute(status -> createExpiredDeployment("legacy", cutoff));
        Deployment independent = tx.execute(status -> createExpiredDeployment("independent", cutoff));
        UUID legacyVersionId = legacy.getComponentVersion().getId();
        UUID flowId = UUID.randomUUID();
        jdbc.update("""
                insert into flow(id,type,state,born_at,component_version_id,final_deployment_environment_id)
                values (?, 'AD_HOC', 'CLOSED', current_timestamp, ?, ?)
                """, flowId, legacyVersionId, legacy.getEnvironment().getId());
        jdbc.update("update deployment set flow_id = ? where id = ?", flowId, legacy.getId());

        var result = retention.deleteCandidates(Set.of(legacy.getId(), independent.getId()), cutoff);

        assertThat(result.deletedDeployments()).isEqualTo(2);
        assertThat(count("deployment", legacy.getId())).isZero();
        assertThat(count("deployment", independent.getId())).isZero();
        assertThat(count("component_version", legacyVersionId)).isEqualTo(1);
        assertThat(count("component_version", independent.getComponentVersion().getId())).isZero();
        assertThat(count("flow", flowId)).isEqualTo(1);
        assertThat(retention.findDeletionCandidates(cutoff, 10)).isEmpty();
        assertThat(retention.findPendingRefreshTasks(10)).hasSize(1);

        // Simulate release 2 on the same running repository instance, without an existence cache.
        jdbc.execute("alter table deployment drop column flow_id");
        jdbc.execute("drop table flow");
        Deployment afterMigration = tx.execute(status -> createExpiredDeployment("after-migration", cutoff));

        assertThat(retention.deleteCandidates(Set.of(afterMigration.getId()), cutoff).deletedDeployments()).isEqualTo(1);
        assertThat(count("deployment", afterMigration.getId())).isZero();
        assertThat(count("component_version", afterMigration.getComponentVersion().getId())).isZero();
        assertThat(retention.findPendingRefreshTasks(10)).hasSize(2);
    }

    private Deployment createExpiredDeployment(String name, ZonedDateTime cutoff) {
        Environment environment = environments.save(new Environment(name));
        Component component = components.save(new Component(name,
                systems.save(new ch.admin.bit.jeap.deploymentlog.domain.System(name))));
        Deployment deployment = TestDataFactory.createDeployment(environment, component, cutoff.minusDays(1),
                "1.0", TestDataFactory.createDeploymentTarget());
        deployment.success(cutoff.minusHours(23), "done");
        return deployments.save(deployment);
    }

    private long count(String table, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where id = ?", Long.class, id);
    }
}
