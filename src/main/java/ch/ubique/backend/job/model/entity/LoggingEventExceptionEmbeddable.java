package ch.ubique.backend.job.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class LoggingEventExceptionEmbeddable {

    @Column(name = "i", nullable = false)
    private int i;

    @Column(name = "trace_line")
    private String traceLine;

    public int getI() {
        return i;
    }

    public String getTraceLine() {
        return traceLine;
    }
}
