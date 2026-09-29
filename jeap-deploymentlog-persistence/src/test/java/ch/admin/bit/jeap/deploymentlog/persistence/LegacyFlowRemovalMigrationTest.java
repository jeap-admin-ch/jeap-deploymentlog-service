package ch.admin.bit.jeap.deploymentlog.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyFlowRemovalMigrationTest {
    @Test
    void removesLegacySchemaWhilePreservingDeploymentHistoryAndSnapshots() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR", "sa", "");
        Flyway.configure().dataSource(source).target("33").load().migrate();
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


        jdbc.update("update deployment set staging_type = 'AD_HOC' where id = ?", deployment);
        jdbc.update("insert into deployment_final_environments(deployment_id,environment_name) values (?, 'PROD')",
                deployment);
        jdbc.update("""
                insert into environment_component_version_state(id,component_id,component_version_id,deployment_id,environment_id)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), component, version, deployment, environment);
        var deploymentBefore = jdbc.queryForMap("select * from deployment where id = ?", deployment);
        deploymentBefore.remove("FLOW_ID");
        var versionsBefore = jdbc.queryForList("select * from component_version");
        var targetsBefore = jdbc.queryForList("select * from deployment_final_environments");
        var snapshotsBefore = jdbc.queryForList("select * from environment_component_version_state");
        var typesBefore = jdbc.queryForList("select * from deployment_types");

        Flyway.configure().dataSource(source).load().migrate();

        assertThat(jdbc.queryForMap("select * from deployment where id = ?", deployment)).isEqualTo(deploymentBefore);
        assertThat(jdbc.queryForList("select * from component_version")).isEqualTo(versionsBefore);
        assertThat(jdbc.queryForList("select * from deployment_final_environments")).isEqualTo(targetsBefore);
        assertThat(jdbc.queryForList("select * from environment_component_version_state")).isEqualTo(snapshotsBefore);
        assertThat(jdbc.queryForList("select * from deployment_types")).isEqualTo(typesBefore);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables where lower(table_name) = 'flow'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = 'deployment' and lower(column_name) = 'flow_id'
                """, Integer.class)).isZero();
    }
}
