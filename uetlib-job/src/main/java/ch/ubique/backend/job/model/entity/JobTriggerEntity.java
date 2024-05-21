package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.converters.TriggerDefinitionToStringConverter;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

@Table(name = "t_job_trigger")
@Entity
public class JobTriggerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_trigger_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = TriggerDefinitionToStringConverter.class)
    @Column(name = "trigger_definition", nullable = false)
    private TriggerDefinition triggerDefinition;

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
}
