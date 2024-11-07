package ch.ubique.backend.job.logging;

/** copied and adapted from logback-classic {@link ch.qos.logback.classic.db.names.ColumnName} */
enum ColumnName {
    EVENT_ID,

    TIMESTMP,
    FORMATTED_MESSAGE,
    LOGGER_NAME,
    LEVEL_STRING,
    THREAD_NAME,
    REFERENCE_FLAG,
    ARG0,
    ARG1,
    ARG2,
    ARG3,
    CALLER_FILENAME,
    CALLER_CLASS,
    CALLER_METHOD,
    CALLER_LINE,
    JOB_RUN_ID,

    // MDC
    MAPPED_KEY,
    MAPPED_VALUE,

    // Exceptions
    I,
    TRACE_LINE;
}
