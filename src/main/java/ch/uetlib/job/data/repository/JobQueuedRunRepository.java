package ch.uetlib.job.data.repository;

import ch.uetlib.job.model.entity.JobQueuedRunEntity;
import ch.uetlib.job.model.queue.JobRunQueueState;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    @Query(
            """
                    SELECT q
                    FROM JobQueuedRunEntity q
                    INNER JOIN JobEntity job ON job.id = q.jobId
                    WHERE q.queueState = :state
                    ORDER BY COALESCE(q.priority, job.priority) DESC, q.createdAt ASC
                    """)
    Slice<JobQueuedRunEntity> findByQueueStateOrderedByPriority(
            JobRunQueueState state, Pageable pageable);

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
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.lastHeartbeat = :now
                    WHERE j.jobRunId = :jobRunId AND j.queueState = ch.uetlib.job.model.queue.JobRunQueueState.RUNNING
                    """)
    void touchHeartbeatByJobRunId(int jobRunId, Instant now);

    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :resetState, j.runner = null, j.lastHeartbeat = null, j.updatedAt = :now
                    WHERE j.jobId = :jobId AND j.queueState = :staleState
                      AND (j.lastHeartbeat IS NULL OR j.lastHeartbeat < :staleBefore)
                    """)
    int resetStaleClaimsForJob(
            int jobId,
            JobRunQueueState staleState,
            JobRunQueueState resetState,
            Instant staleBefore,
            Instant now);

    /** Unconditionally resets a single row - for defensive/internal use only. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.queueState = :newState, j.runner = null, j.lastHeartbeat = null, j.updatedAt = :now
                    WHERE j.jobQueuedRunId = :id
                    """)
    void forceReset(int id, JobRunQueueState newState, Instant now);

    /**
     * Never updates a {@link JobRunQueueState#RUNNING} row's priority: callers must pass it as
     * {@code excludedState} - reordering an already-running run is meaningless.
     */
    @Modifying(clearAutomatically = true)
    @Query(
            """
                    UPDATE JobQueuedRunEntity j
                    SET j.priority = :priority
                    WHERE j.jobQueuedRunId = :id AND j.queueState <> :excludedState
                    """)
    int updatePriority(int id, @Nullable Integer priority, JobRunQueueState excludedState);
}
