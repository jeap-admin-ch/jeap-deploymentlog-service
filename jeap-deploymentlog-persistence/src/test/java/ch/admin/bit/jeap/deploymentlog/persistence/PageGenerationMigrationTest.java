package ch.admin.bit.jeap.deploymentlog.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PageGenerationMigrationTest {

    @Test
    void upgradeFromV29DoesNotClassifyOrSuppressHistoricalDeployments() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;NON_KEYWORDS=year";
        try (SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, "sa", "", true)) {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("29").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            UUID missingPageDeployment = UUID.randomUUID();
            UUID trackedDeployment = UUID.randomUUID();
            for (UUID id : new UUID[]{missingPageDeployment, trackedDeployment}) {
                jdbc.update("insert into deployment (id, last_modified, started_at, state) " +
                        "values (?, timestamp '2026-09-15 10:00:00', timestamp '2026-09-15 09:00:00', 'SUCCESS')", id);
            }
            jdbc.update("insert into deployment_page (id, deployment_id, deployment_state_timestamp, page_id) " +
                    "values (?, ?, timestamp '2026-09-15 10:00:00', 'existing-page')", UUID.randomUUID(), trackedDeployment);

            Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

            for (UUID id : new UUID[]{missingPageDeployment, trackedDeployment}) {
                assertThat(jdbc.queryForObject("select page_generation_legacy_unclassified from deployment where id = ?",
                        Boolean.class, id)).isFalse();
                assertThat(jdbc.queryForObject("select page_generation_suppressed from deployment where id = ?",
                        Boolean.class, id)).isFalse();
                assertThat(jdbc.queryForObject("select page_generation_request_id from deployment where id = ?",
                        UUID.class, id)).isNull();
                assertThat(jdbc.queryForObject("select state from deployment where id = ?", String.class, id))
                        .isEqualTo("SUCCESS");
            }
            assertThat(jdbc.queryForObject("select count(*) from deployment", Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("select page_id from deployment_page where deployment_id = ?",
                    String.class, trackedDeployment)).isEqualTo("existing-page");

            // A subsequent startup must neither rerun V30 nor modify suppression recorded by housekeeping.
            jdbc.update("update deployment set page_generation_suppressed = true where id = ?", missingPageDeployment);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("select page_generation_suppressed from deployment where id = ?",
                    Boolean.class, missingPageDeployment)).isTrue();
        }
    }
}
