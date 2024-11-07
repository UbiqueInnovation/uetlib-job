package ch.ubique.backend.job.logging;

import ch.qos.logback.classic.db.SQLBuilder;
import ch.qos.logback.classic.db.names.*;

/** copied and adapted from logback-classic {@link SQLBuilder} */
@SuppressWarnings("java:S1192")
public final class JobSQLBuilder {

    private JobSQLBuilder() {}

    static String buildInsertPropertiesSQL(final DBNameResolver dbNameResolver) {
        final var joinedLoggingEventPropertyColumns =
                String.join(
                        ", ",
                        dbNameResolver.getColumnName(ColumnName.EVENT_ID),
                        dbNameResolver.getColumnName(ColumnName.MAPPED_KEY),
                        dbNameResolver.getColumnName(ColumnName.MAPPED_VALUE));
        return "INSERT INTO "
                + dbNameResolver.getTableName(TableName.LOGGING_EVENT_PROPERTY)
                + " ("
                + joinedLoggingEventPropertyColumns
                + ") "
                + "VALUES (?, ?, ?)";
    }

    static String buildInsertExceptionSQL(final DBNameResolver dbNameResolver) {
        final var joinedLoggingEventExceptionColumns =
                String.join(
                        ", ",
                        dbNameResolver.getColumnName(ColumnName.EVENT_ID),
                        dbNameResolver.getColumnName(ColumnName.I),
                        dbNameResolver.getColumnName(ColumnName.TRACE_LINE));
        return "INSERT INTO "
                + dbNameResolver.getTableName(TableName.LOGGING_EVENT_EXCEPTION)
                + " ("
                + joinedLoggingEventExceptionColumns
                + ") "
                + "VALUES (?, ?, ?)";
    }

    static String buildInsertSQL(final DBNameResolver dbNameResolver) {
        final var joinedLoggingEventColumns =
                String.join(
                        ", ",
                        dbNameResolver.getColumnName(ColumnName.TIMESTMP),
                        dbNameResolver.getColumnName(ColumnName.FORMATTED_MESSAGE),
                        dbNameResolver.getColumnName(ColumnName.LOGGER_NAME),
                        dbNameResolver.getColumnName(ColumnName.LEVEL_STRING),
                        dbNameResolver.getColumnName(ColumnName.THREAD_NAME),
                        dbNameResolver.getColumnName(ColumnName.REFERENCE_FLAG),
                        dbNameResolver.getColumnName(ColumnName.ARG0),
                        dbNameResolver.getColumnName(ColumnName.ARG1),
                        dbNameResolver.getColumnName(ColumnName.ARG2),
                        dbNameResolver.getColumnName(ColumnName.ARG3),
                        dbNameResolver.getColumnName(ColumnName.CALLER_FILENAME),
                        dbNameResolver.getColumnName(ColumnName.CALLER_CLASS),
                        dbNameResolver.getColumnName(ColumnName.CALLER_METHOD),
                        dbNameResolver.getColumnName(ColumnName.CALLER_LINE),
                        dbNameResolver.getColumnName(ColumnName.JOB_RUN_ID));
        return "INSERT INTO "
                + dbNameResolver.getTableName(TableName.LOGGING_EVENT)
                + " ("
                + joinedLoggingEventColumns
                + ") VALUES (?, ?, ? ,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }
}
