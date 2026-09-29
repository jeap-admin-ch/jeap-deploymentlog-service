package ch.admin.bit.jeap.deploymentlog.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class VersionHistoryMigrationTest {
    @Test void expandsSchemaAndPreservesLegacyWritesWithoutReclassification() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR", "sa", "");
        Flyway.configure().dataSource(source).target("32").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID system = UUID.randomUUID();
        UUID component = UUID.randomUUID();
        UUID environment = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID flow = UUID.randomUUID();
        UUID deployment = UUID.randomUUID();
        jdbc.update("insert into system(id,name) values (?, 'SYS')", system);
        jdbc.update("insert into component(id,name,active,system_id) values (?, 'service',true,?)", component, system);
        jdbc.update("insert into environment(id,name,productive,development,staging_order) values (?, 'PROD',true,false,3)", environment);
        jdbc.update("""
                insert into component_version(id,version_name,component_id,committed_at,published_version)
                values (?, '1.0', ?, current_timestamp, true)
                """, version, component);
        jdbc.update("""
                insert into flow(id,type,state,born_at,component_version_id,final_deployment_environment_id)
                values (?, 'AD_HOC', 'CLOSED', current_timestamp, ?, ?)
                """, flow, version, environment);
        jdbc.update("""
                insert into deployment(id,external_id,component_version_id,environment_id,started_at,ended_at,
                    last_modified,state,sequence,flow_id)
                values (?, 'historic', ?, ?, current_timestamp, current_timestamp, current_timestamp, 'SUCCESS', 'NEW', ?)
                """, deployment, version, environment, flow);
        jdbc.update("insert into deployment_types(deployment_id,type) values (?, 'CODE')", deployment);

        Flyway.configure().dataSource(source).target("33").load().migrate();

        assertThat(jdbc.queryForObject("select staging_type from deployment where id = ?", String.class, deployment)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from deployment_final_environments", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name = 'DEPLOYMENT_STAGING_HISTORY'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select state from deployment where id = ?", String.class, deployment))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns where table_name = 'DEPLOYMENT'
                and column_name in ('STAGING_RELEVANT', 'AUTO_STAGING_TO_END')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select flow_id from deployment where id = ?", UUID.class, deployment))
                .isEqualTo(flow);
        assertThat(jdbc.queryForObject("select state from flow where id = ?", String.class, flow)).isEqualTo("CLOSED");

        UUID legacyFlow = UUID.randomUUID();
        jdbc.update("""
                insert into flow(id,type,state,born_at,component_version_id,final_deployment_environment_id)
                values (?, 'AD_HOC', 'OPEN', current_timestamp, ?, ?)
                """, legacyFlow, version, environment);
        UUID legacyDeployment = UUID.randomUUID();
        jdbc.update("""
                insert into deployment(id,external_id,component_version_id,environment_id,started_at,
                    last_modified,state,sequence,flow_id)
                values (?, 'legacy-after-upgrade', ?, ?, current_timestamp, current_timestamp, 'STARTED', 'NEW', ?)
                """, legacyDeployment, version, environment, legacyFlow);
        assertThat(jdbc.queryForObject("select staging_type from deployment where id = ?",
                String.class, legacyDeployment)).isNull();

        UUID newDeployment = UUID.randomUUID();
        jdbc.update("""
                insert into deployment(id,external_id,component_version_id,environment_id,started_at,
                    last_modified,state,sequence,staging_type)
                values (?, 'new-after-upgrade', ?, ?, current_timestamp, current_timestamp, 'STARTED', 'NEW', 'AD_HOC')
                """, newDeployment, version, environment);
        jdbc.update("insert into deployment_final_environments(deployment_id,environment_name) values (?, 'PROD')",
                newDeployment);
        assertThat(jdbc.queryForObject("select flow_id from deployment where id = ?", UUID.class, newDeployment)).isNull();
        assertThat(jdbc.queryForObject("select environment_name from deployment_final_environments where deployment_id = ?",
                String.class, newDeployment)).isEqualTo("PROD");
    }
}
