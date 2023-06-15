package ch.ubique.backend.job.model.entity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;

import jakarta.persistence.*;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Table(name = "logging_event")
@Entity
public class LoggingEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "timestmp", nullable = false)
    private long timestamp;

    @Column(name = "formatted_message", nullable = false)
    private String formattedMessage;

    @Column(name = "logger_name", nullable = false)
    private String loggerName;

    @Column(name = "level_string", nullable = false)
    private String levelString;

    @Column(name = "thread_name")
    private String threadName;

    @Column(name = "reference_flag")
    private Integer referenceFlag;

    @Column(name = "arg0")
    private String arg0;

    @Column(name = "arg1")
    private String arg1;

    @Column(name = "arg2")
    private String arg2;

    @Column(name = "arg3")
    private String arg3;

    @Column(name = "caller_filename", nullable = false)
    private String callerFilename;

    @Column(name = "caller_class", nullable = false)
    private String callerClass;

    @Column(name = "caller_method", nullable = false)
    private String callerMethod;

    @Column(name = "caller_line", nullable = false)
    private String callerLine;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "logging_event_exception", joinColumns = @JoinColumn(name = "event_id"))
    @OrderBy("i")
    private List<LoggingEventExceptionEmbeddable> eventExceptions = new ArrayList<>();

    public LoggingEvent toLoggingEvent() {
        final var event = new LoggingEvent();
        event.setTimeStamp(timestamp);
        event.setMessage(getMessage());
        event.setLoggerName(loggerName);
        event.setLevel(Level.toLevel(levelString));
        event.setThreadName(threadName);
        event.setArgumentArray(
                Stream.of(arg0, arg1, arg2, arg3).filter(Objects::nonNull).toArray());
        return event;
    }

    @NotNull
    private String getMessage() {
        return Stream.concat(
                        Stream.of(formattedMessage),
                        eventExceptions.stream().map(LoggingEventExceptionEmbeddable::getTraceLine))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));
    }
}
