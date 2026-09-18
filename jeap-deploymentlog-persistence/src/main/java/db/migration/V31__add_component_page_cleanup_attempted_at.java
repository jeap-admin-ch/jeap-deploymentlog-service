package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

@SuppressWarnings("java:S101") // Flyway requires versioned Java migrations to follow this naming convention.
public class V31__add_component_page_cleanup_attempted_at extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    ALTER TABLE component_page
                    ADD COLUMN cleanup_attempted_at TIMESTAMP
                    """);
        }
    }
}
