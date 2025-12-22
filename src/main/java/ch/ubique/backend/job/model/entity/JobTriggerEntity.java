package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.converters.TriggerDefinitionToStringConverter;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "t_job_trigger")
public class JobTriggerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_trigger_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private int jobId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = TriggerDefinitionToStringConverter.class)
    @Column(name = "trigger_definition", nullable = false)
    private TriggerDefinition triggerDefinition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobTriggerEntity() {}

    public JobTriggerEntity(final int jobId, final TriggerDefinition triggerDefinition) {
        this(null, jobId, triggerDefinition);
    }

    public JobTriggerEntity(final Trigger trigger) {
        this(trigger.id(), trigger.jobId(), trigger.definition());
    }

    private JobTriggerEntity(
            final Integer id, final int jobId, final TriggerDefinition triggerDefinition) {
        this.id = id;
        this.jobId = jobId;
        this.triggerDefinition = Objects.requireNonNull(triggerDefinition);
        this.type = Objects.requireNonNull(triggerDefinition.type());
    }

    @Nullable
    public Trigger toTrigger() {
        if (triggerDefinition == null) {
            return null;
        }
        return new Trigger(id, jobId, triggerDefinition);
    }

    public JobTriggerEntity update(final Trigger trigger) {
        this.triggerDefinition = trigger.definition();
        this.type = trigger.definition().type();
        return this;
    }
}
