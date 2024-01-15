package ch.ubique.backend.job.logging;

import static ch.qos.logback.core.db.DBHelper.closeStatement;

import ch.qos.logback.classic.db.DBHelper;
import ch.qos.logback.classic.db.names.DBNameResolver;
import ch.qos.logback.classic.db.names.DefaultDBNameResolver;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.db.DBAppenderBase;

import com.pivovarit.function.exception.WrappedException;

import org.apache.commons.lang3.math.NumberUtils;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** copied and adapted from logback-classic */
public class JobDBAppender extends DBAppenderBase<ILoggingEvent> {
    protected static final Method GET_GENERATED_KEYS_METHOD;

    static final int TIMESTMP_INDEX = 1;
    static final int FORMATTED_MESSAGE_INDEX = 2;
    static final int LOGGER_NAME_INDEX = 3;
    static final int LEVEL_STRING_INDEX = 4;
    static final int THREAD_NAME_INDEX = 5;
    static final int REFERENCE_FLAG_INDEX = 6;
    static final int ARG0_INDEX = 7;
    static final int ARG1_INDEX = 8;
    static final int ARG2_INDEX = 9;
    static final int ARG3_INDEX = 10;
    static final int CALLER_FILENAME_INDEX = 11;
    static final int CALLER_CLASS_INDEX = 12;
    static final int CALLER_METHOD_INDEX = 13;
    static final int CALLER_LINE_INDEX = 14;
    static final int EVENT_ID_INDEX = 15;
    static final int JOB_RUN_ID_INDEX = 16;
    static final StackTraceElement EMPTY_CALLER_DATA = CallerData.naInstance();

    static {
        try {
            //noinspection rawtypes
            GET_GENERATED_KEYS_METHOD =
                    PreparedStatement.class.getMethod("getGeneratedKeys", (Class[]) null);
        } catch (final NoSuchMethodException e) {
            throw new WrappedException(e);
        }
    }

    protected String insertPropertiesSQL;
    protected String insertExceptionSQL;
    protected String insertSQL;
    private DBNameResolver dbNameResolver;

    public void setDbNameResolver(final DBNameResolver dbNameResolver) {
        this.dbNameResolver = dbNameResolver;
    }

    @Override
    public void start() {
        if (dbNameResolver == null) {
            dbNameResolver = new DefaultDBNameResolver();
        }
        insertExceptionSQL = JobSQLBuilder.buildInsertExceptionSQL(dbNameResolver);
        insertPropertiesSQL = JobSQLBuilder.buildInsertPropertiesSQL(dbNameResolver);
        insertSQL = JobSQLBuilder.buildInsertSQL(dbNameResolver);
        super.start();
    }

    @Override
    protected void subAppend(
            final ILoggingEvent event,
            final Connection connection,
            final PreparedStatement insertStatement)
            throws Throwable {

        bindLoggingEventWithInsertStatement(insertStatement, event);
        bindLoggingEventArgumentsWithPreparedStatement(insertStatement, event.getArgumentArray());

        // This is expensive... should we do it every time?
        bindCallerDataWithPreparedStatement(insertStatement, event.getCallerData());

        final var updateCount = insertStatement.executeUpdate();
        if (updateCount != 1) {
            addWarn("Failed to insert loggingEvent");
        }
    }

    protected void secondarySubAppend(
            final ILoggingEvent event, final Connection connection, final long eventId)
            throws Throwable {
        final var mergedMap = mergePropertyMaps(event);
        insertProperties(mergedMap, connection, eventId);
        if (event.getThrowableProxy() != null) {
            insertThrowable(event.getThrowableProxy(), connection, eventId);
        }
    }

    void bindLoggingEventWithInsertStatement(
            final PreparedStatement stmt, final ILoggingEvent event) throws SQLException {
        stmt.setLong(TIMESTMP_INDEX, event.getTimeStamp());
        stmt.setString(FORMATTED_MESSAGE_INDEX, event.getFormattedMessage());
        stmt.setString(LOGGER_NAME_INDEX, event.getLoggerName());
        stmt.setString(LEVEL_STRING_INDEX, event.getLevel().toString());
        stmt.setString(THREAD_NAME_INDEX, event.getThreadName());
        stmt.setShort(REFERENCE_FLAG_INDEX, DBHelper.computeReferenceMask(event));
        stmt.setObject(
                JOB_RUN_ID_INDEX,
                NumberUtils.createInteger(event.getMDCPropertyMap().get("jobRunId")));
    }

    void bindLoggingEventArgumentsWithPreparedStatement(
            final PreparedStatement stmt, final Object[] argArray) throws SQLException {

        final var arrayLen = argArray != null ? argArray.length : 0;

        for (var i = 0; i < arrayLen && i < 4; i++) {
            stmt.setString(ARG0_INDEX + i, asStringTruncatedTo254(argArray[i]));
        }
        if (arrayLen < 4) {
            for (var i = arrayLen; i < 4; i++) {
                stmt.setString(ARG0_INDEX + i, null);
            }
        }
    }

    String asStringTruncatedTo254(final Object o) {
        String s = null;
        if (o != null) {
            s = o.toString();
        }

        if (s == null) {
            return null;
        }
        if (s.length() <= 254) {
            return s;
        }
        return s.substring(0, 254);
    }

    void bindCallerDataWithPreparedStatement(
            final PreparedStatement stmt, final StackTraceElement[] callerDataArray)
            throws SQLException {

        final var caller = extractFirstCaller(callerDataArray);

        stmt.setString(CALLER_FILENAME_INDEX, caller.getFileName());
        stmt.setString(CALLER_CLASS_INDEX, caller.getClassName());
        stmt.setString(CALLER_METHOD_INDEX, caller.getMethodName());
        stmt.setString(CALLER_LINE_INDEX, Integer.toString(caller.getLineNumber()));
    }

    private StackTraceElement extractFirstCaller(final StackTraceElement[] callerDataArray) {
        if (hasAtLeastOneNonNullElement(callerDataArray)) {
            return callerDataArray[0];
        }
        return EMPTY_CALLER_DATA;
    }

    private boolean hasAtLeastOneNonNullElement(final StackTraceElement[] callerDataArray) {
        return callerDataArray != null && callerDataArray.length > 0 && callerDataArray[0] != null;
    }

    Map<String, String> mergePropertyMaps(final ILoggingEvent event) {
        final Map<String, String> mergedMap = new HashMap<>();
        // we add the context properties first, then the event properties, since
        // we consider that event-specific properties should have priority over
        // context-wide properties.
        final var loggerContextMap = event.getLoggerContextVO().getPropertyMap();
        final var mdcMap = event.getMDCPropertyMap();
        if (loggerContextMap != null) {
            mergedMap.putAll(loggerContextMap);
        }
        if (mdcMap != null) {
            mergedMap.putAll(mdcMap);
        }

        return mergedMap;
    }

    @Override
    protected Method getGeneratedKeysMethod() {
        return GET_GENERATED_KEYS_METHOD;
    }

    @Override
    protected String getInsertSQL() {
        return insertSQL;
    }

    protected void insertProperties(
            final Map<String, String> mergedMap, final Connection connection, final long eventId)
            throws SQLException {
        final var propertiesKeys = mergedMap.keySet();
        if (!propertiesKeys.isEmpty()) {
            PreparedStatement insertPropertiesStatement = null;
            try {
                insertPropertiesStatement = connection.prepareStatement(insertPropertiesSQL);

                for (final var key : propertiesKeys) {
                    final var value = mergedMap.get(key);

                    insertPropertiesStatement.setLong(1, eventId);
                    insertPropertiesStatement.setString(2, key);
                    insertPropertiesStatement.setString(3, value);

                    if (cnxSupportsBatchUpdates) {
                        insertPropertiesStatement.addBatch();
                    } else {
                        insertPropertiesStatement.execute();
                    }
                }

                if (cnxSupportsBatchUpdates) {
                    insertPropertiesStatement.executeBatch();
                }
            } finally {
                closeStatement(insertPropertiesStatement);
            }
        }
    }

    /**
     * Add an exception statement either as a batch or execute immediately if batch updates are not
     * supported.
     */
    void updateExceptionStatement(
            final PreparedStatement exceptionStatement,
            final String txt,
            final short i,
            final long eventId)
            throws SQLException {
        exceptionStatement.setLong(1, eventId);
        exceptionStatement.setShort(2, i);
        exceptionStatement.setString(3, txt);
        if (cnxSupportsBatchUpdates) {
            exceptionStatement.addBatch();
        } else {
            exceptionStatement.execute();
        }
    }

    short buildExceptionStatement(
            final IThrowableProxy tp,
            short baseIndex,
            final PreparedStatement insertExceptionStatement,
            final long eventId)
            throws SQLException {

        final var buf = new StringBuilder();
        ThrowableProxyUtil.subjoinFirstLine(buf, tp);
        updateExceptionStatement(insertExceptionStatement, buf.toString(), baseIndex, eventId);
        baseIndex++;

        final var commonFrames = tp.getCommonFrames();
        final var stepArray = tp.getStackTraceElementProxyArray();
        for (var i = 0; i < stepArray.length - commonFrames; i++) {
            final var sb = new StringBuilder();
            sb.append(CoreConstants.TAB);
            ThrowableProxyUtil.subjoinSTEP(sb, stepArray[i]);
            updateExceptionStatement(insertExceptionStatement, sb.toString(), baseIndex, eventId);
            baseIndex++;
        }

        if (commonFrames > 0) {
            updateExceptionStatement(
                    insertExceptionStatement,
                    CoreConstants.TAB + "... " + commonFrames + " common frames omitted",
                    baseIndex,
                    eventId);
            baseIndex++;
        }

        return baseIndex;
    }

    protected void insertThrowable(
            IThrowableProxy tp, final Connection connection, final long eventId)
            throws SQLException {

        PreparedStatement exceptionStatement = null;
        try {
            //noinspection SqlSourceToSinkFlow
            exceptionStatement = connection.prepareStatement(insertExceptionSQL);

            short baseIndex = 0;
            while (tp != null) {
                baseIndex = buildExceptionStatement(tp, baseIndex, exceptionStatement, eventId);
                tp = tp.getCause();
            }

            if (cnxSupportsBatchUpdates) {
                exceptionStatement.executeBatch();
            }
        } finally {
            closeStatement(exceptionStatement);
        }
    }
}
