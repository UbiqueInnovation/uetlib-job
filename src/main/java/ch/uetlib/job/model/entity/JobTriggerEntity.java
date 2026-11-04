package ch.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.uetlib.job.model.converters.TriggerDefinitionToStringConverter;
import ch.uetlib.job.model.triggers.Trigger;
import ch.uetlib.job.model.triggers.TriggerDefinition;
import ch.uetlib.job.model.triggers.TriggerType;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_job_trigger")
public class JobTriggerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_trigger_id", nullable = false)
    private @Nullable Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private int jobId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private @Nullable TriggerType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = TriggerDefinitionToStringConverter.class)
    @Column(name = "trigger_definition", nullable = false)
    private @Nullable TriggerDefinition triggerDefinition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    public JobTriggerEntity() {}

    public JobTriggerEntity(final int jobId, final TriggerDefinition triggerDefinition) {
        this(null, jobId, triggerDefinition);
    }

    public JobTriggerEntity(final Trigger trigger) {
        this(trigger.id(), trigger.jobId(), trigger.definition());
    }

    private JobTriggerEntity(
            final @Nullable Integer id,
            final int jobId,
            final TriggerDefinition triggerDefinition) {
        this.id = id;
        this.jobId = jobId;
        this.triggerDefinition = requireNonNull(triggerDefinition);
        this.type = requireNonNull(triggerDefinition.type());
    }

    @Nullable
    public Trigger toTrigger() {
        if (triggerDefinition == null) {
            return null;
        }
        return new Trigger(requireNonNull(id), jobId, triggerDefinition);
    }

    public JobTriggerEntity update(final Trigger trigger) {
        this.triggerDefinition = trigger.definition();
        this.type = trigger.definition().type();
        return this;
    }
}
