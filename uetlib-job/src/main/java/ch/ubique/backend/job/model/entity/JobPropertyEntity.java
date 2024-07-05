package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Example;

import java.time.Instant;

@Entity
@IdClass(JobPropertyId.class)
@Table(name = "t_job_properties")
public class JobPropertyEntity {

    @Id
    @Column(name = "fk_job_id", nullable = false)
    private int jobId;

    @Id
    @Column(name = "\"key\"", nullable = false)
    private String key;

    @Column(name = "value")
    private String value;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static <T> JobPropertyEntity from(final int jobId, final JobProperty<T> property) {
        final var entity = new JobPropertyEntity();
        entity.jobId = jobId;
        entity.key = property.key();
        entity.setValue(property);
        return entity;
    }

    public <T> JobPropertyEntity setValue(final JobProperty<T> property) {
        this.value = property.encodedValue();
        return this;
    }

    public static Example<JobPropertyEntity> keyExample(
            final int jobId, final JobProperty<?> property) {
        final var entity = new JobPropertyEntity();
        entity.jobId = jobId;
        entity.key = property.key();
        return Example.of(entity);
    }

    public JobProperty<Object> toProperty() {
        return JobPropertyDefinitions.get(key).createPropertyFromString(getValue());
    }

    public String getValue() {
        return StringUtils.defaultIfBlank(value, null);
    }
}
