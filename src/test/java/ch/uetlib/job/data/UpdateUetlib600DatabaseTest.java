package ch.uetlib.job.data;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.service.inputs.JobRunInputManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/**
 * An existing database gets 6.0.0 from {@code updates/update_uetlib_7_0_0.sql}, a new one from
 * {@code V7_0_0__complete_setup.sql}; both must end up with the same schema. The 6.0.0 fresh
 * install differs from 4.0.0's only by the objects the update adds, so removing those from a fresh
 * install and running the update script is the upgrade path - on every database.
 */
class UpdateUetlib600DatabaseTest extends BaseJobDatabaseTest {

    private static final List<String> TABLES = List.of("t_job_run_queue", "t_job_instance");

    @Autowired DataSource dataSource;
    @MockitoBean JobRunInputManager jobRunInputManager;

    @Test
    void theUpdateScriptCreatesExactlyWhatAFreshInstallHas() throws SQLException, IOException {
        // Postgres rolls this DDL back with the test's transaction. MySQL and MariaDB commit DDL
        // at once, so there the database is left as found only because the update recreates it.
        final var connection = DataSourceUtils.getConnection(dataSource);
        try {
            final var vendor = vendor(connection);
            final var freshInstall = describe(connection);
            assertFalse(freshInstall.isEmpty());

            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE t_job_run_queue");
                statement.execute("DROP TABLE t_job_instance");
                if ("postgresql".equals(vendor)) {
                    statement.execute("DROP FUNCTION notify_on_job_run_queue_insert()");
                }
            }
            runUpdateScript(connection, vendor);

            assertEquals(freshInstall, describe(connection));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /** Flyway's {@code {vendor}}: postgresql, mysql or mariadb. */
    private static String vendor(final Connection connection) throws SQLException {
        return connection.getMetaData().getURL().split(":")[1];
    }

    private static void runUpdateScript(final Connection connection, final String vendor)
            throws SQLException, IOException {
        final var script =
                new ClassPathResource(
                        "db/migration/templates/%s/updates/update_uetlib_7_0_0.sql"
                                .formatted(vendor));
        if ("postgresql".equals(vendor)) {
            // Its function body holds semicolons; the Postgres driver splits statements itself.
            try (var statement = connection.createStatement()) {
                statement.execute(script.getContentAsString(StandardCharsets.UTF_8));
            }
        } else {
            ScriptUtils.executeSqlScript(connection, script);
        }
    }

    /** Columns, indexes, foreign keys and triggers of the tables 6.0.0 adds, sorted. */
    private static List<String> describe(final Connection connection) throws SQLException {
        final var metaData = connection.getMetaData();
        final var catalog = connection.getCatalog();
        final var schema = connection.getSchema();
        final var description = new ArrayList<String>();
        for (final var table : TABLES) {
            try (var columns = metaData.getColumns(catalog, schema, table, null)) {
                while (columns.next()) {
                    description.add(
                            "%s column %s %s nullable=%s default=%s autoincrement=%s"
                                    .formatted(
                                            table,
                                            columns.getString("COLUMN_NAME"),
                                            columns.getString("TYPE_NAME"),
                                            columns.getString("IS_NULLABLE"),
                                            columns.getString("COLUMN_DEF"),
                                            columns.getString("IS_AUTOINCREMENT")));
                }
            }
            try (var indexes = metaData.getIndexInfo(catalog, schema, table, false, false)) {
                while (indexes.next()) {
                    description.add(
                            "%s index %s on %s unique=%s"
                                    .formatted(
                                            table,
                                            indexes.getString("INDEX_NAME"),
                                            indexes.getString("COLUMN_NAME"),
                                            !indexes.getBoolean("NON_UNIQUE")));
                }
            }
            try (var keys = metaData.getImportedKeys(catalog, schema, table)) {
                while (keys.next()) {
                    description.add(
                            "%s foreign key %s -> %s.%s on delete %s"
                                    .formatted(
                                            table,
                                            keys.getString("FKCOLUMN_NAME"),
                                            keys.getString("PKTABLE_NAME"),
                                            keys.getString("PKCOLUMN_NAME"),
                                            keys.getShort("DELETE_RULE")));
                }
            }
        }
        try (var statement = connection.createStatement();
                var triggers =
                        statement.executeQuery(
                                "SELECT trigger_name, event_manipulation, event_object_table"
                                        + " FROM information_schema.triggers"
                                        + " WHERE event_object_table IN ('t_job_run_queue',"
                                        + " 't_job_instance')")) {
            while (triggers.next()) {
                description.add(
                        "trigger %s %s on %s"
                                .formatted(
                                        triggers.getString(1),
                                        triggers.getString(2),
                                        triggers.getString(3)));
            }
        }
        description.sort(null);
        return description;
    }
}
