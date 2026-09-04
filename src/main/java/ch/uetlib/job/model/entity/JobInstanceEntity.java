package ch.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.uetlib.job.model.JobInstance;
import ch.uetlib.job.model.converters.StringSetToStringConverter;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

/**
 * {@code primarySlot} (the lease) is read-only here, so a check-in never overwrites a lease change.
 */
@Entity
@Table(name = "t_job_instance")
public class JobInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_instance_id", nullable = false, updatable = false)
    private @Nullable Integer jobInstanceId;

    @NotBlank
    @Column(name = "instance_name", nullable = false, updatable = false)
    private String instanceName;

    @NotBlank
    @Column(name = "version", nullable = false)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = StringSetToStringConverter.class)
    @Column(name = "taints", nullable = false)
    private Set<String> taints;

    @Column(name = "primary_candidate", nullable = false)
    private boolean primaryCandidate;

    @PositiveOrZero
    @Column(name = "trigger_count")
    private @Nullable Integer triggerCount;

    @Column(name = "primary_slot", insertable = false, updatable = false)
    private @Nullable Integer primarySlot;

    @Column(name = "primary_since", insertable = false, updatable = false)
    private @Nullable Instant primarySince;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected JobInstanceEntity() {}

    public JobInstanceEntity(
            final String instanceName,
            final String version,
            final Set<String> taints,
            final boolean primaryCandidate,
            final @Nullable Integer triggerCount,
            final Instant startedAt,
            final Instant now) {
        this.instanceName = instanceName;
        this.version = version;
        this.taints = taints;
        this.primaryCandidate = primaryCandidate;
        this.triggerCount = triggerCount;
        this.startedAt = startedAt;
        this.updatedAt = now;
    }

    /**
     * Everything but the lease; also {@code startedAt}, since a restarted pod may keep its name.
     */
    public JobInstanceEntity checkIn(
            final String version,
            final Set<String> taints,
            final boolean primaryCandidate,
            final @Nullable Integer triggerCount,
            final Instant startedAt,
            final Instant now) {
        this.version = version;
        this.taints = taints;
        this.primaryCandidate = primaryCandidate;
        this.triggerCount = triggerCount;
        this.startedAt = startedAt;
        this.updatedAt = now;
        return this;
    }

    public JobInstance toJobInstance() {
        return new JobInstance(
                requireNonNull(jobInstanceId),
                instanceName,
                version,
                Set.copyOf(taints),
                primaryCandidate,
                triggerCount,
                primarySlot != null,
                primarySince,
                startedAt,
                updatedAt);
    }
}
