package ch.ubique.backend.job.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "logging_event_property")
public class LoggingEventPropertyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "mapped_key", nullable = false)
    private String mappedKey;

    @Column(name = "mapped_value")
    private String mappedValue;

    public long eventId() {
        return eventId;
    }

    public String mappedKey() {
        return mappedKey;
    }

    public String mappedValue() {
        return mappedValue;
    }
}
