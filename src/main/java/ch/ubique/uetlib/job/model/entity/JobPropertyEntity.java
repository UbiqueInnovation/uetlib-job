package ch.ubique.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.job.model.properties.JobProperty;
import ch.ubique.uetlib.job.model.properties.JobPropertyDefinitions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

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
    private @Nullable String key;

    @Column(name = "value")
    private @Nullable String value;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

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

    public JobProperty<Object> toProperty() {
        return JobPropertyDefinitions.get(requireNonNull(key)).createPropertyFromString(getValue());
    }

    @Nullable
    public String getValue() {
        return StringUtils.defaultIfBlank(value, null);
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
