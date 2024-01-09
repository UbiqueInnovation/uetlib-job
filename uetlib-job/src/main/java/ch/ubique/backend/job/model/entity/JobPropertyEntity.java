package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import jakarta.persistence.*;

import org.apache.commons.lang3.StringUtils;

@Entity
@Table(name = "t_job_properties")
public class JobPropertyEntity {

    @EmbeddedId private JobPropertyId id;

    @MapsId("jobId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_job_id", nullable = false)
    private JobEntity job;

    @Column(name = "value")
    private String value;

    public static <T> JobPropertyEntity from(final JobEntity job, final JobProperty<T> property) {
        final var entity = new JobPropertyEntity();
        entity.id = JobPropertyId.of(job.getId(), property.key());
        entity.job = job;
        entity.value = property.encodedValue();
        return entity;
    }

    public JobProperty<Object> toProperty() {
        return JobPropertyDefinitions.get(id.getKey()).createPropertyFromString(getValue());
    }

    public String getValue() {
        return StringUtils.defaultIfBlank(value, null);
    }
}
