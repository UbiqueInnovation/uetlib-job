package ch.ubique.backend.job.data.repository;

import ch.ubique.backend.job.model.entity.JobQueuedRunEntity;
import ch.ubique.backend.job.model.queue.JobRunQueueState;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Repository
public interface JobQueuedRunRepository
        extends JpaRepository<JobQueuedRunEntity, Integer>,
                JpaSpecificationExecutor<JobQueuedRunEntity> {
    List<JobQueuedRunEntity> findAllByJobId(int jobId);

    List<JobQueuedRunEntity> findAllByQueueState(JobRunQueueState queueState);

    /** Never deletes a {@link JobRunQueueState#RUNNING} row, regardless of its own state. */
    long deleteByJobQueuedRunIdAndQueueStateNot(int jobQueuedRunId, JobRunQueueState excludedState);

    /**
     * Never deletes a {@link JobRunQueueState#RUNNING} row: callers must exclude it from {@code
     * states}.
     */
    long deleteByJobIdInAndQueueStateIn(
            Collection<Integer> jobIds, Collection<JobRunQueueState> states);

    Optional<JobQueuedRunEntity> findFirstByOrderByCreatedAtAsc();

    Optional<JobQueuedRunEntity> findFirstByOrderByCreatedAtDesc();

    /**
     * A queued run's {@code taints} must be a subset of {@code instanceTaints} to match. A row is
     * excluded if it has any taint outside {@code instanceTaints}; that "any" is computed via a
     * {@code json_table(...)} lateral join local to the subquery's own {@code FROM} clause - not
     * correlated to the outer query - since Hibernate cannot translate a {@code JSON_TABLE(...)}
     * correlated to an outer alias inside a {@code NOT EXISTS}/{@code NOT IN} subquery's {@code
     * FROM} clause ("Could not construct set-returning function. Maybe you forgot to use
     * 'lateral'?"). The same restriction is why the taint-count tiebreaker below is a correlated
     * scalar subquery whose own {@code json_table(...)} is only lateral-joined to its own local
     * root ({@code q3}), with the correlation to the outer row expressed as a plain {@code WHERE}
     * equality instead - there is no dedicated "json array length" HQL function, so counting the
     * exploded {@code json_table} rows is the only portable way to get it.
     */
    @Query(
            """
                    SELECT q
                    FROM JobQueuedRunEntity q
                    INNER JOIN JobEntity job ON job.id = q.jobId
                    WHERE q.jobQueuedRunId NOT IN
                    (
                        SELECT q2.jobQueuedRunId
                        FROM JobQueuedRunEntity q2
                        JOIN LATERAL json_table(
                            q2.taints,
                            '$[*]' COLUMNS (
                                taint String PATH '$'
                            )
                        ) not_taints
                        WHERE not_taints.taint NOT IN :instanceTaints
                    )
                    ORDER BY COALESCE(q.priority, job.priority) DESC,
                    (
                        SELECT COUNT(existing_taints.taint)
                        FROM JobQueuedRunEntity q3
                        JOIN LATERAL json_table(
                            q3.taints,
                            '$[*]' COLUMNS (
                                taint String PATH '$'
                            )
                        ) existing_taints
                        WHERE q3.jobQueuedRunId = q.jobQueuedRunId
                    ) DESC
                    """)
    Stream<JobQueuedRunEntity> findByTaints(Set<String> instanceTaints, final Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = :runner, j.lastHeartbeat = :now
                    WHERE j.jobQueuedRunId = :id AND j.queueState = :previousState
                    """)
    int setState(
            int id,
            JobRunQueueState previousState,
            JobRunQueueState newState,
            String runner,
            Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE JobQueuedRunEntity j SET j.lastHeartbeat = :now WHERE j.jobQueuedRunId = :id")
    void touchHeartbeat(int id, Instant now);

    /** Unconditionally resets a single row - for defensive/internal use only. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = null, j.lastHeartbeat = null, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                    """)
    void forceReset(int id, JobRunQueueState newState, Instant now);
}
