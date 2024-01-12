package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
        this.type = triggerDefinition.type();
        this.triggerDefinition = triggerDefinition;
    }

    public Trigger toTrigger() {
        return new Trigger(id, jobId, triggerDefinition);
    }
}
