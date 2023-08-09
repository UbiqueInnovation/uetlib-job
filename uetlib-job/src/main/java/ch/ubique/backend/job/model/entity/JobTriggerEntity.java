package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerDefinition;
import ch.ubique.backend.job.model.triggers.TriggerType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.*;

import java.io.IOException;

@Table(name = "t_job_trigger")
@Entity
public class JobTriggerEntity {

    private static final ObjectMapper objectMapper = ObjectMapperUtils.objectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_trigger_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType type;

    @Column(name = "data", nullable = false)
    private String data;

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
        try {
            this.data = objectMapper.writeValueAsString(triggerDefinition);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Trigger toTrigger() {
        try {
            return new Trigger(id, jobId, objectMapper.readValue(data, type.defintionClass()));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
