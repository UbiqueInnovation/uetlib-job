package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.apache.commons.lang3.StringUtils;

@Entity
@IdClass(JobPropertyId.class)
@Table(name = "t_job_properties")
public class JobPropertyEntity {

    @Id
    @Column(name = "fk_job_id", nullable = false)
    private int jobId;

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "value")
    private String value;

    public static <T> JobPropertyEntity from(final int jobId, final JobProperty<T> property) {
        final var entity = new JobPropertyEntity();
        entity.jobId = jobId;
        entity.value = property.encodedValue();
        entity.key = property.key();
        return entity;
    }

    public JobProperty<Object> toProperty() {
        return JobPropertyDefinitions.get(key).createPropertyFromString(getValue());
    }

    public String getValue() {
        return StringUtils.defaultIfBlank(value, null);
    }
}
