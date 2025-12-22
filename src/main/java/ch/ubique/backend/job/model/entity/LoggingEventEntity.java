package ch.ubique.backend.job.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

@Entity
@Table(name = "t_logging_event")
public class LoggingEventEntity {

    public LoggingEventEntity() {}

    public LoggingEventEntity(int jobRunId, Instant timestamp, String formattedMessage) {
        this.jobRunId = jobRunId;
        this.timestamp = timestamp;
        this.formattedMessage = formattedMessage;
    }

    @Id
    @Positive
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_logging_event_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_run_id", nullable = false)
    private Integer jobRunId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "formatted_message", nullable = false)
    private String formattedMessage;

    public Instant getTimestamp() {
        return timestamp;
    }
}
