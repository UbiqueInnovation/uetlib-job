package ch.ubique.backend.job.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_logging_event")
public class LoggingEventEntity {

    public LoggingEventEntity() {}

    public LoggingEventEntity(final int jobRunId, final Instant timestamp, final String formattedMessage) {
        this.jobRunId = jobRunId;
        this.timestamp = timestamp;
        this.formattedMessage = formattedMessage;
    }

    @Id
    @Positive
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_logging_event_id", nullable = false)
    private @Nullable Integer id;

    @Column(name = "fk_job_run_id", nullable = false)
    private @Nullable Integer jobRunId;

    @Column(name = "timestamp", nullable = false)
    private @Nullable Instant timestamp;

    @Column(name = "formatted_message", nullable = false)
    private @Nullable String formattedMessage;

    @Nullable
    public Instant getTimestamp() {
        return timestamp;
    }
}
